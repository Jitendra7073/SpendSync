import { LlmError, type JsonSchema, type LlmChunk, type LlmMessage, type LlmProvider, type LlmRequest, type ToolCall } from './types';
import { readSse } from './sse';

const BASE = 'https://generativelanguage.googleapis.com/v1beta';

type Part = Record<string, unknown>;
interface Content {
  role: 'user' | 'model';
  parts: Part[];
}

/** Gemini's schema is an OpenAPI subset: no additionalProperties, enum-style upper-case types. */
export function toGeminiSchema(schema: JsonSchema): JsonSchema | undefined {
  const walk = (node: unknown): unknown => {
    if (Array.isArray(node)) return node.map(walk);
    if (node && typeof node === 'object') {
      const out: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(node as Record<string, unknown>)) {
        if (k === 'additionalProperties' || k === '$schema') continue;
        out[k] = k === 'type' && typeof v === 'string' ? v.toUpperCase() : walk(v);
      }
      return out;
    }
    return node;
  };
  const cleaned = walk(schema) as JsonSchema;
  const props = cleaned.properties as Record<string, unknown> | undefined;
  // A tool with no arguments must omit `parameters` entirely.
  if (!props || Object.keys(props).length === 0) return undefined;
  return cleaned;
}

function parseMaybeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** Builds the request body. Consecutive same-role turns are merged: Gemini wants strict alternation. */
export function toGeminiBody(req: LlmRequest, systemAsUser: boolean): Record<string, unknown> {
  const contents: Content[] = [];
  const push = (role: Content['role'], parts: Part[]) => {
    if (parts.length === 0) return;
    const last = contents[contents.length - 1];
    if (last && last.role === role) last.parts.push(...parts);
    else contents.push({ role, parts: [...parts] });
  };

  for (const m of req.messages as LlmMessage[]) {
    if (m.role === 'user') push('user', [{ text: m.text }]);
    else if (m.role === 'assistant') {
      const parts: Part[] = [];
      if (m.text) parts.push({ text: m.text });
      for (const c of m.toolCalls) parts.push((c.raw as Part) ?? { functionCall: { name: c.name, args: c.args } });
      push('model', parts);
    } else {
      push(
        'user',
        m.results.map((r) => ({
          functionResponse: { name: r.name, response: r.isError ? { error: r.content } : { result: parseMaybeJson(r.content) } },
        })),
      );
    }
  }

  if (systemAsUser) {
    const first = contents.find((c) => c.role === 'user');
    const firstText = first?.parts.find((p) => typeof p.text === 'string');
    if (firstText) firstText.text = `${req.system}\n\n---\n\n${firstText.text}`;
    else contents.unshift({ role: 'user', parts: [{ text: req.system }] });
  }

  const declarations = req.tools.map((t) => {
    const parameters = toGeminiSchema(t.parameters);
    return parameters ? { name: t.name, description: t.description, parameters } : { name: t.name, description: t.description };
  });

  return {
    contents,
    ...(systemAsUser ? {} : { systemInstruction: { parts: [{ text: req.system }] } }),
    ...(declarations.length ? { tools: [{ functionDeclarations: declarations }] } : {}),
    generationConfig: { maxOutputTokens: req.maxTokens, temperature: req.temperature ?? 0.3 },
  };
}

function retryDelayMs(body: unknown): number | undefined {
  const details = (body as { error?: { details?: Array<{ retryDelay?: string }> } })?.error?.details;
  const delay = details?.find((d) => typeof d.retryDelay === 'string')?.retryDelay;
  const secs = delay ? parseFloat(delay) : NaN;
  return Number.isFinite(secs) ? secs * 1000 : undefined;
}

export interface GeminiOptions {
  apiKey: string;
  model: string;
  label: string;
  fetchFn?: typeof fetch;
}

/**
 * Google AI Studio (Gemini API). Used for the free Gemma 4 models and free Gemini Flash models.
 * Adapts by itself to models that reject system instructions (folds them into the first user
 * message and remembers) and reports `no_tools` when a model rejects function calling.
 */
export function geminiProvider(opts: GeminiOptions): LlmProvider {
  const doFetch = opts.fetchFn ?? fetch;
  let systemAsUser = false;

  async function* attempt(req: LlmRequest): AsyncGenerator<LlmChunk> {
    let response: Response;
    try {
      response = await doFetch(`${BASE}/models/${opts.model}:streamGenerateContent?alt=sse`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-goog-api-key': opts.apiKey },
        body: JSON.stringify(toGeminiBody(req, systemAsUser)),
        signal: req.signal,
      });
    } catch (err) {
      throw new LlmError(req.signal?.aborted ? 'timeout' : 'network', String(err));
    }

    if (!response.ok) {
      const text = await response.text().catch(() => '');
      let json: unknown;
      try {
        json = JSON.parse(text);
      } catch {
        /* not json */
      }
      const message = (json as { error?: { message?: string } })?.error?.message ?? text.slice(0, 200);
      const s = response.status;
      if (s === 429) throw new LlmError('rate_limit', message, retryDelayMs(json));
      if (s === 401 || s === 403) throw new LlmError('auth', message);
      if (s === 404) throw new LlmError('not_found', message);
      if (s >= 500) throw new LlmError('server', message);
      if (s === 400 && /(system|developer) instruction/i.test(message) && !systemAsUser) {
        systemAsUser = true;
        throw new SystemRetry();
      }
      if (s === 400 && req.tools.length > 0) throw new LlmError('no_tools', message);
      throw new LlmError('bad_output', message);
    }

    let calls = 0;
    let sawText = false;
    let finish: string | undefined;
    let usage: { input: number; output: number } | undefined;
    let sawToolCall = false;

    for await (const payload of readSse(response)) {
      let chunk: {
        candidates?: Array<{ content?: { parts?: Part[] }; finishReason?: string }>;
        usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number };
        promptFeedback?: { blockReason?: string };
        error?: { message?: string; code?: number };
      };
      try {
        chunk = JSON.parse(payload);
      } catch {
        continue;
      }
      if (chunk.error) throw new LlmError(chunk.error.code === 429 ? 'rate_limit' : 'server', chunk.error.message ?? 'stream error');
      if (chunk.promptFeedback?.blockReason) throw new LlmError('bad_output', `blocked: ${chunk.promptFeedback.blockReason}`);
      if (chunk.usageMetadata) {
        usage = { input: chunk.usageMetadata.promptTokenCount ?? 0, output: chunk.usageMetadata.candidatesTokenCount ?? 0 };
      }
      const cand = chunk.candidates?.[0];
      for (const part of cand?.content?.parts ?? []) {
        if (part.thought === true) continue; // reasoning summaries are never shown
        if (typeof part.text === 'string' && part.text) {
          sawText = true;
          yield { type: 'text', text: part.text };
        } else if (part.functionCall && typeof part.functionCall === 'object') {
          const fc = part.functionCall as { name?: string; args?: unknown; id?: string };
          if (!fc.name) continue;
          sawToolCall = true;
          const call: ToolCall = { id: fc.id ?? `call_${++calls}`, name: fc.name, args: fc.args ?? {}, raw: part };
          yield { type: 'tool_call', call };
        }
      }
      if (cand?.finishReason) finish = cand.finishReason;
    }

    if (!sawText && !sawToolCall) {
      throw new LlmError('bad_output', `empty answer (${finish ?? 'no finish reason'})`);
    }
    yield {
      type: 'end',
      reason: sawToolCall ? 'tool_calls' : finish === 'MAX_TOKENS' ? 'length' : 'stop',
      usage,
    };
  }

  return {
    id: `gemini:${opts.model}`,
    provider: 'gemini',
    label: opts.label,
    model: opts.model,
    paid: false,
    async *stream(req: LlmRequest): AsyncGenerator<LlmChunk> {
      try {
        yield* attempt(req);
      } catch (err) {
        if (!(err instanceof SystemRetry)) throw err;
        yield* attempt(req); // once more, with the instructions folded into the first message
      }
    },
  };
}

/** Internal signal: the model refused system instructions, so retry with them in the first user turn. */
class SystemRetry extends Error {}
