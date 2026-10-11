import { describe, expect, it } from 'vitest';
import { toGeminiBody } from './gemini';
import { toOpenAiMessages } from './openai-compat';
import type { LlmRequest } from './types';

const req = (images?: { mime: 'image/jpeg'; base64: string }[]): LlmRequest => ({
  system: 'sys', tools: [], maxTokens: 100, messages: [{ role: 'user', text: 'read this', images }],
});

describe('images in model requests', () => {
  it('Gemini gets inlineData parts after the text', () => {
    const body = toGeminiBody(req([{ mime: 'image/jpeg', base64: 'AAA' }]), false) as { contents: Array<{ parts: unknown[] }> };
    expect(body.contents[0].parts).toEqual([{ text: 'read this' }, { inlineData: { mimeType: 'image/jpeg', data: 'AAA' } }]);
  });
  it('OpenAI-compatible gets a content array with a data URL', () => {
    const msgs = toOpenAiMessages(req([{ mime: 'image/jpeg', base64: 'AAA' }]));
    expect(msgs[1].content).toEqual([{ type: 'text', text: 'read this' }, { type: 'image_url', image_url: { url: 'data:image/jpeg;base64,AAA' } }]);
  });
  it('text-only messages are unchanged', () => {
    expect(toOpenAiMessages(req())[1].content).toBe('read this');
    const body = toGeminiBody(req(), false) as { contents: Array<{ parts: unknown[] }> };
    expect(body.contents[0].parts).toEqual([{ text: 'read this' }]);
  });
});
