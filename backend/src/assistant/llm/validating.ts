import { LlmError, type LlmChunk, type LlmProvider, type LlmRequest } from './types';

/**
 * Buffers a provider's whole reply and checks it. Invalid output becomes `bad_output`, which the router treats
 * like any failure (next model, short cool-down). Used where only a complete, valid JSON answer is useful.
 */
export function validating(p: LlmProvider, validate: (text: string) => void): LlmProvider {
  return {
    id: p.id, label: p.label, provider: p.provider, model: p.model, paid: p.paid,
    async *stream(req: LlmRequest): AsyncGenerator<LlmChunk> {
      let text = '';
      let end: LlmChunk = { type: 'end', reason: 'stop' };
      for await (const c of p.stream(req)) {
        if (c.type === 'text') text += c.text;
        else if (c.type === 'end') end = c;
      }
      try {
        validate(text);
      } catch (e) {
        throw new LlmError('bad_output', e instanceof Error ? e.message.slice(0, 200) : 'invalid output');
      }
      yield { type: 'text', text };
      yield end;
    },
  };
}
