import { describe, expect, it } from 'vitest';
import { buildProviders, parseChain } from './chain';
import { geminiProvider, toGeminiBody } from './gemini';
import { openAiCompatProvider, toOpenAiMessages } from './openai-compat';
import { LlmError, type LlmChunk, type LlmRequest } from './types';

const sseResponse = (events: unknown[], status = 200) =>
  new Response(events.map((e) => `data: ${typeof e === 'string' ? e : JSON.stringify(e)}\n\n`).join(''), { status });

const req: LlmRequest = {
  system: 'SYS',
  maxTokens: 100,
  tools: [{ name: 'get_balance', description: 'd', parameters: { type: 'object', properties: {}, additionalProperties: false } }],
  messages: [
    { role: 'user', text: 'hi' },
    { role: 'assistant', text: '', toolCalls: [{ id: 'c1', name: 'get_balance', args: {} }] },
    { role: 'tool', results: [{ callId: 'c1', name: 'get_balance', content: '{"net":5}', isError: false }] },
  ],
};

async function collect(it: AsyncGenerator<LlmChunk>) {
  const out: LlmChunk[] = [];
  for await (const c of it) out.push(c);
  return out;
}

const gem = (fetchFn: typeof fetch) => geminiProvider({ apiKey: 'k', model: 'm', label: 'M', fetchFn });
const oai = (fetchFn: typeof fetch) => openAiCompatProvider({ provider: 'g', model: 'm', label: 'M', baseUrl: 'http://x', apiKey: 'k', fetchFn });

describe('gemini adapter', () => {
  it('builds a valid body: system instruction, no-arg tools without parameters, alternating roles', () => {
    const body = toGeminiBody(req, false) as {
      contents: Array<{ role: string }>;
      systemInstruction: unknown;
      tools: Array<{ functionDeclarations: Array<Record<string, unknown>> }>;
    };
    expect(body.contents.map((c) => c.role)).toEqual(['user', 'model', 'user']);
    expect(body.tools[0].functionDeclarations[0]).not.toHaveProperty('parameters');
    expect(body.systemInstruction).toBeTruthy();
  });

  it('folds the system prompt into the first user turn for models that reject it', () => {
    const body = toGeminiBody(req, true) as { contents: Array<{ parts: Array<{ text?: string }> }>; systemInstruction?: unknown };
    expect(body.systemInstruction).toBeUndefined();
    expect(body.contents[0].parts[0].text).toContain('SYS');
  });

  it('streams text and tool calls, skipping thought parts', async () => {
    const p = gem(async () =>
      sseResponse([
        { candidates: [{ content: { parts: [{ text: 'secret', thought: true }, { text: 'Hello' }] } }] },
        {
          candidates: [{ content: { parts: [{ functionCall: { name: 'get_balance', args: {} } }] }, finishReason: 'STOP' }],
          usageMetadata: { promptTokenCount: 3, candidatesTokenCount: 2 },
        },
      ]),
    );
    const chunks = await collect(p.stream(req));
    expect(chunks.map((c) => c.type)).toEqual(['text', 'tool_call', 'end']);
    expect(chunks[0]).toEqual({ type: 'text', text: 'Hello' });
    expect(chunks[2]).toMatchObject({ reason: 'tool_calls' });
  });

  it('retries once with the system prompt in the user turn when system instructions are rejected', async () => {
    let n = 0;
    const p = gem(async () =>
      n++ === 0
        ? new Response(JSON.stringify({ error: { message: 'Developer instruction is not enabled for this model' } }), { status: 400 })
        : sseResponse([{ candidates: [{ content: { parts: [{ text: 'ok' }] }, finishReason: 'STOP' }] }]),
    );
    const chunks = await collect(p.stream({ ...req, tools: [] }));
    expect(n).toBe(2);
    expect(chunks[0]).toEqual({ type: 'text', text: 'ok' });
  });

  it('maps HTTP errors to failover kinds', async () => {
    const kind = async (status: number, tools = req.tools) => {
      const p = gem(async () => new Response('{"error":{"message":"x"}}', { status }));
      return collect(p.stream({ ...req, tools })).catch((e: LlmError) => e.kind);
    };
    expect(await kind(429)).toBe('rate_limit');
    expect(await kind(403)).toBe('auth');
    expect(await kind(404)).toBe('not_found');
    expect(await kind(503)).toBe('server');
    expect(await kind(400)).toBe('no_tools');
    expect(await kind(400, [])).toBe('bad_output');
  });

  it('treats an empty answer as a failure so the next model is tried', async () => {
    const p = gem(async () => sseResponse([{ candidates: [{ finishReason: 'SAFETY' }] }]));
    await expect(collect(p.stream(req))).rejects.toMatchObject({ kind: 'bad_output' });
  });
});

describe('openai-compatible adapter', () => {
  it('converts history to chat messages with tool_calls and tool results', () => {
    const m = toOpenAiMessages(req);
    expect(m.map((x) => x.role)).toEqual(['system', 'user', 'assistant', 'tool']);
    expect(m[2].tool_calls?.[0].function.name).toBe('get_balance');
    expect(m[3].tool_call_id).toBe('c1');
  });

  it('assembles streamed tool-call fragments and ends with tool_calls', async () => {
    const p = oai(async () =>
      sseResponse([
        { choices: [{ delta: { content: 'Hi ' } }] },
        { choices: [{ delta: { tool_calls: [{ index: 0, id: 'a', function: { name: 'get_bal', arguments: '{"x"' } }] } }] },
        { choices: [{ delta: { tool_calls: [{ index: 0, function: { name: 'ance', arguments: ':1}' } }] }, finish_reason: 'tool_calls' }] },
        '[DONE]',
      ]),
    );
    const chunks = await collect(p.stream(req));
    expect(chunks[1]).toMatchObject({ type: 'tool_call', call: { id: 'a', name: 'get_balance', args: { x: 1 } } });
    expect(chunks.at(-1)).toMatchObject({ type: 'end', reason: 'tool_calls' });
  });

  it('classifies malformed tool JSON and tool_use_failed as bad_output, 429 as rate_limit', async () => {
    const bad = oai(async () => sseResponse([{ choices: [{ delta: { tool_calls: [{ index: 0, id: 'a', function: { name: 'f', arguments: '{oops' } }] } }] }]));
    await expect(collect(bad.stream(req))).rejects.toMatchObject({ kind: 'bad_output' });

    const failed = oai(async () => new Response('{"error":{"code":"tool_use_failed","message":"bad call"}}', { status: 400 }));
    await expect(collect(failed.stream(req))).rejects.toMatchObject({ kind: 'bad_output' });

    const limited = oai(async () => new Response('{}', { status: 429, headers: { 'retry-after': '7' } }));
    await expect(collect(limited.stream(req))).rejects.toMatchObject({ kind: 'rate_limit', retryAfterMs: 7000 });
  });
});

describe('provider chain', () => {
  it('only activates providers whose key is set, in order, and never paid ones by default', () => {
    const ids = buildProviders({ GEMINI_API_KEY: 'a', GROQ_API_KEY: 'b', ANTHROPIC_API_KEY: 'c' }).map((p) => p.id);
    expect(ids).toEqual([
      'gemini:gemma-4-26b-a4b-it',
      'gemini:gemma-4-31b-it',
      'gemini:gemini-2.5-flash-lite',
      'groq:openai/gpt-oss-120b',
      'groq:qwen/qwen3.8-27b',
      'groq:openai/gpt-oss-20b',
    ]);
  });

  it('adds Claude only with an explicit opt-in', () => {
    const ids = buildProviders({ ANTHROPIC_API_KEY: 'c', ASSISTANT_ALLOW_PAID: 'true' }).map((p) => p.id);
    expect(ids).toEqual(['anthropic:claude-haiku-4-5']);
  });

  it('lets ASSISTANT_CHAIN override the order and ignores junk entries', () => {
    expect(parseChain('groq:foo, nope, bad:x,gemini:bar').map((e) => `${e.provider}:${e.model}`)).toEqual(['groq:foo', 'gemini:bar']);
  });

  it('no keys means no providers (the built-in offline answer takes over)', () => {
    expect(buildProviders({})).toEqual([]);
  });
});
