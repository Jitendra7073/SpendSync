import { describe, expect, it } from 'vitest';
import { buildVisionProviders } from './chain';
import { validating } from './validating';
import { LlmError, type LlmChunk, type LlmProvider } from './types';

const fake = (out: string): LlmProvider => ({
  id: 'f:m', label: 'F', provider: 'f', model: 'm', paid: false,
  async *stream(): AsyncGenerator<LlmChunk> { yield { type: 'text', text: out.slice(0, 3) }; yield { type: 'text', text: out.slice(3) }; yield { type: 'end', reason: 'stop' }; },
});
const drain = async (p: LlmProvider) => { let t = ''; for await (const c of p.stream({ system: '', messages: [], tools: [], maxTokens: 1 })) if (c.type === 'text') t += c.text; return t; };

describe('vision chain', () => {
  it('uses only vision models and honours BILL_AI_CHAIN', () => {
    expect(buildVisionProviders({ GEMINI_API_KEY: 'k' }).map((p) => p.id)).toEqual(['gemini:gemini-2.5-flash-lite', 'gemini:gemma-4-31b-it']);
    expect(buildVisionProviders({ GEMINI_API_KEY: 'k', BILL_AI_CHAIN: 'gemini:gemma-4-31b-it' }).map((p) => p.id)).toEqual(['gemini:gemma-4-31b-it']);
    expect(buildVisionProviders({ GEMINI_API_KEY: 'k', ASSISTANT_CHAIN: 'gemini:x' }).map((p) => p.id)).toContain('gemini:gemini-2.5-flash-lite');
  });
});

describe('validating', () => {
  it('passes valid output through whole', async () => {
    expect(await drain(validating(fake('{"ok":1}'), (t) => JSON.parse(t)))).toBe('{"ok":1}');
  });
  it('turns invalid output into bad_output so the router moves on', async () => {
    await expect(drain(validating(fake('nope'), (t) => JSON.parse(t)))).rejects.toMatchObject({ kind: 'bad_output' });
    expect(LlmError).toBeDefined();
  });
});
