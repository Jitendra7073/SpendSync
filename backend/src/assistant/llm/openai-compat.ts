import { LlmError, type LlmChunk, type LlmMessage, type LlmProvider, type LlmRequest, type ToolCall } from './types';
import { readSse, retryAfterMs } from './sse';

interface OaiMessage {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content?: string | null;
  tool_calls?: Array<{ id: string; type: 'function'; function: { name: string; arguments: string } }>;
  tool_call_id?: string;
}

export function toOpenAiMessages(req: LlmRequest): OaiMessage[] {
  const out: OaiMessage[] = [{ role: 'system', content: req.system }];
  for (const m of req.messages as LlmMessage[]) {
    if (m.role === 'user') out.push({ role: 'user', content: m.text });
    else if (m.role === 'assistant') {
      out.push({
        role: 'assistant',
        content: m.text || null,
        ...(m.toolCalls.length
          ? { tool_calls: m.toolCalls.map((c) => ({ id: c.id, type: 'function' as const, function: { name: c.name, arguments: JSON.stringify(c.args ?? {}) } })) }
          : {}),
      });
    } else {
      for (const r of m.results) out.push({ role: 'tool', tool_call_id: r.callId, content: r.content });
    }
  }
  return out;
}

export interface OpenAiCompatOptions {
  /** e.g. "groq", "cerebras", "openrouter", "mistral" */
  provider: string;
  model: string;
  label: string;
  baseUrl: string;
  apiKey: string;
  extraHeaders?: Record<string, string>;
  fetchFn?: typeof fetch;
}

/** Any provider that speaks the OpenAI chat-completions streaming format (Groq, Cerebras, OpenRouter, Mistral…). */
export function openAiCompatProvider(opts: OpenAiCompatOptions): LlmProvider {
  const doFetch = opts.fetchFn ?? fetch;

  return {
    id: `${opts.provider}:${opts.model}`,
    provider: opts.provider,
    label: opts.label,
    model: opts.model,
    paid: false,
    async *stream(req: LlmRequest): AsyncGenerator<LlmChunk> {
      let response: Response;
      try {
        response = await doFetch(`${opts.baseUrl.replace(/\/$/, '')}/chat/completions`, {
          method: 'POST',
          headers: { 'content-type': 'application/json', authorization: `Bearer ${opts.apiKey}`, ...opts.extraHeaders },
          body: JSON.stringify({
            model: opts.model,
            messages: toOpenAiMessages(req),
            stream: true,
            max_tokens: req.maxTokens,
            temperature: req.temperature ?? 0.3,
            ...(req.tools.length
              ? { tools: req.tools.map((t) => ({ type: 'function', function: { name: t.name, description: t.description, parameters: t.parameters } })) }
              : {}),
          }),
          signal: req.signal,
        });
      } catch (err) {
        throw new LlmError(req.signal?.aborted ? 'timeout' : 'network', String(err));
      }

      if (!response.ok) {
        const text = await response.text().catch(() => '');
        let code = '';
        let message = text.slice(0, 200);
        try {
          const j = JSON.parse(text) as { error?: { code?: string | number; message?: string } };
          code = String(j.error?.code ?? '');
          message = j.error?.message ?? message;
        } catch {
          /* not json */
        }
        const s = response.status;
        const wait = retryAfterMs(response);
        if (s === 429 || s === 402) throw new LlmError('rate_limit', message, wait);
        if (s === 401 || s === 403) throw new LlmError('auth', message);
        if (s === 404) throw new LlmError('not_found', message);
        if (s >= 500) throw new LlmError('server', message);
        // Small models sometimes write a malformed tool call; that is this answer's fault, not the provider's.
        if (code === 'tool_use_failed') throw new LlmError('bad_output', message);
        if (s === 400 && req.tools.length > 0) throw new LlmError('no_tools', message);
        throw new LlmError('bad_output', message);
      }

      const pending = new Map<number, { id: string; name: string; args: string }>();
      let sawText = false;
      let finish: string | undefined;
      let usage: { input: number; output: number } | undefined;

      for await (const payload of readSse(response)) {
        if (payload.trim() === '[DONE]') break;
        let chunk: {
          choices?: Array<{
            delta?: { content?: string | null; tool_calls?: Array<{ index?: number; id?: string; function?: { name?: string; arguments?: string } }> };
            finish_reason?: string | null;
          }>;
          usage?: { prompt_tokens?: number; completion_tokens?: number };
          error?: { message?: string; code?: string | number };
        };
        try {
          chunk = JSON.parse(payload);
        } catch {
          continue;
        }
        if (chunk.error) {
          const c = String(chunk.error.code ?? '');
          throw new LlmError(c === '429' ? 'rate_limit' : 'server', chunk.error.message ?? 'stream error');
        }
        if (chunk.usage) usage = { input: chunk.usage.prompt_tokens ?? 0, output: chunk.usage.completion_tokens ?? 0 };
        const choice = chunk.choices?.[0];
        const delta = choice?.delta;
        if (delta?.content) {
          sawText = true;
          yield { type: 'text', text: delta.content };
        }
        for (const tc of delta?.tool_calls ?? []) {
          const idx = tc.index ?? 0;
          const cur = pending.get(idx) ?? { id: '', name: '', args: '' };
          if (tc.id) cur.id = tc.id;
          if (tc.function?.name) cur.name += tc.function.name;
          if (tc.function?.arguments) cur.args += tc.function.arguments;
          pending.set(idx, cur);
        }
        if (choice?.finish_reason) finish = choice.finish_reason;
      }

      const calls: ToolCall[] = [];
      for (const [idx, p] of [...pending.entries()].sort((a, b) => a[0] - b[0])) {
        if (!p.name) continue;
        let args: unknown = {};
        if (p.args.trim()) {
          try {
            args = JSON.parse(p.args);
          } catch {
            throw new LlmError('bad_output', 'tool call arguments were not valid JSON');
          }
        }
        calls.push({ id: p.id || `call_${idx}`, name: p.name, args });
      }
      if (!sawText && calls.length === 0) throw new LlmError('bad_output', `empty answer (${finish ?? 'no finish reason'})`);
      for (const call of calls) yield { type: 'tool_call', call };
      yield { type: 'end', reason: calls.length ? 'tool_calls' : finish === 'length' ? 'length' : 'stop', usage };
    },
  };
}
