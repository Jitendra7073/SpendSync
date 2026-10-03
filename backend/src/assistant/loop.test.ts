import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.stubEnv('DATABASE_URL', 'postgres://user:pass@localhost:5432/test');
vi.stubEnv('AUTH_SECRET', 'x'.repeat(40));
vi.mock('../db/index', () => ({ db: {} }));
const logged: Array<{ toolName: string; ok: boolean }> = [];
vi.mock('./audit', () => ({ logToolCall: (r: { toolName: string; ok: boolean }) => logged.push(r) }));

import { runAssistant, type AssistantEvent } from './loop';
import { ProviderRouter } from './llm/router';
import { LlmError, type LlmChunk, type LlmProvider, type LlmRequest } from './llm/types';

/** One scripted model turn: chunks to emit, or an error thrown (optionally after some chunks). */
type Turn = { chunks?: LlmChunk[]; fail?: LlmError };
const text = (t: string): LlmChunk => ({ type: 'text', text: t });
const end = (reason: 'stop' | 'tool_calls' | 'length' | 'blocked' = 'stop'): LlmChunk => ({ type: 'end', reason, usage: { input: 100, output: 20 } });
const call = (id: string, name: string, args: unknown): LlmChunk => ({ type: 'tool_call', call: { id, name, args } });

function fakeProvider(id: string, turns: Turn[], requests: LlmRequest[] = []): LlmProvider {
  let i = 0;
  return {
    id,
    label: id,
    provider: id.split(':')[0],
    model: id,
    paid: false,
    async *stream(req) {
      requests.push(structuredClone({ ...req, signal: undefined }));
      const turn = turns[Math.min(i++, turns.length - 1)];
      for (const c of turn.chunks ?? []) yield c;
      if (turn.fail) throw turn.fail;
    },
  };
}

const base = {
  userId: 'u1',
  conversationId: 'conv-1',
  messages: [{ role: 'user' as const, content: 'How do I set a budget?' }],
  context: { today: '2026-10-03', language: 'English' },
};

async function run(providers: LlmProvider[], overrides: Partial<Parameters<typeof runAssistant>[0]> = {}) {
  const events: AssistantEvent[] = [];
  const router = new ProviderRouter(providers);
  await runAssistant({ ...base, ...overrides, router, emit: (e) => events.push(e) });
  return { events, router };
}

const shown = (events: AssistantEvent[]) => events.filter((e) => e.type === 'delta').map((e) => (e as { text: string }).text).join('');

beforeEach(() => {
  logged.length = 0;
});

describe('assistant loop', () => {
  it('answers directly, hides the follow-up tag, names the model and reports usage', async () => {
    const p = fakeProvider('gemini:g', [{ chunks: [text('Open Budget and tap Set.\n<follow'), text('ups>What is near limit?|Show pace</followups>'), end()] }]);
    const { events } = await run([p]);
    expect(shown(events)).toBe('Open Budget and tap Set.');
    expect(events).toContainEqual({ type: 'source', id: 'gemini:g', label: 'gemini:g' });
    expect(events.find((e) => e.type === 'followups')).toEqual({ type: 'followups', items: ['What is near limit?', 'Show pace'] });
    expect(events.at(-1)).toEqual({ type: 'done', usage: { input: 100, output: 20, cacheRead: 0 } });
  });

  it('runs tools, returns results in one message, and offers a screen', async () => {
    const requests: LlmRequest[] = [];
    const p = fakeProvider(
      'gemini:g',
      [
        { chunks: [call('t1', 'search_help', { query: 'set a budget' }), call('t2', 'open_screen', { screen: 'budget' }), end('tool_calls')] },
        { chunks: [text('Go to Budget.'), end()] },
      ],
      requests,
    );
    const { events } = await run([p]);
    expect(events).toContainEqual({ type: 'ui_action', action: { type: 'open_screen', screen: 'budget' } });
    expect(events.filter((e) => e.type === 'tool' && e.status === 'done').length).toBe(2);
    expect(logged.map((l) => l.toolName).sort()).toEqual(['open_screen', 'search_help']);
    const last = requests[1].messages.at(-1)!;
    expect(last.role).toBe('tool');
    expect(last.role === 'tool' && last.results.length).toBe(2);
  });

  it('puts per-request context on the last user turn, not in the system prompt', async () => {
    const requests: LlmRequest[] = [];
    await run([fakeProvider('gemini:g', [{ chunks: [text('Hi'), end()] }], requests)]);
    const user = requests[0].messages.at(-1) as { text: string };
    expect(user.text).toContain('default_reply_language: English');
    expect(requests[0].system).not.toContain('2026-10-03');
  });

  it('refuses unknown tools and bad arguments without crashing', async () => {
    const requests: LlmRequest[] = [];
    const p = fakeProvider(
      'gemini:g',
      [
        { chunks: [call('a', 'delete_account', {}), call('b', 'search_transactions', { limit: 9999 }), end('tool_calls')] },
        { chunks: [text('Sorry.'), end()] },
      ],
      requests,
    );
    const { events } = await run([p]);
    const last = requests[1].messages.at(-1)!;
    expect(last.role === 'tool' && last.results.every((r) => r.isError)).toBe(true);
    expect(events.filter((e) => e.type === 'tool' && e.status === 'failed').length).toBe(2);
    expect(events.at(-1)?.type).toBe('done');
  });

  it('never offers write or blocked tools, and drops tools the user turned off', async () => {
    const requests: LlmRequest[] = [];
    const ctx = { ...base.context, prefs: { model: 'auto', disabledTools: ['get_balance'], style: 'balanced' as const, tone: 'friendly' as const, instructions: '' } };
    await run([fakeProvider('gemini:g', [{ chunks: [text('ok'), end()] }], requests)], { context: ctx });
    const names = requests[0].tools.map((t) => t.name);
    expect(names).toContain('get_spending_summary');
    expect(names).not.toContain('get_balance');
    expect(names.some((n) => /delete|clear|sign_?out|create|update|add_/.test(n))).toBe(false);
  });

  it('enforces a disabled tool even if the model calls it anyway', async () => {
    const requests: LlmRequest[] = [];
    const ctx = { ...base.context, prefs: { model: 'auto', disabledTools: ['get_balance'], style: 'balanced' as const, tone: 'friendly' as const, instructions: '' } };
    const p = fakeProvider('gemini:g', [{ chunks: [call('x', 'get_balance', {}), end('tool_calls')] }, { chunks: [text('ok'), end()] }], requests);
    await run([p], { context: ctx });
    expect(logged.some((l) => l.ok)).toBe(false);
    const last = requests[1].messages.at(-1)!;
    expect(last.role === 'tool' && last.results[0].isError).toBe(true);
  });

  it('stops on refusal and on truncation without running tools', async () => {
    const refused = await run([fakeProvider('gemini:g', [{ chunks: [end('blocked')] }])]);
    expect(refused.events.at(-1)).toEqual({ type: 'error', code: 'refused' });
    const truncated = await run([fakeProvider('gemini:g', [{ chunks: [text('Partial'), call('x', 'get_balance', {}), end('length')] }])]);
    expect(logged.length).toBe(0);
    expect(truncated.events.at(-1)?.type).toBe('done');
  });

  it('caps runaway tool loops', async () => {
    const requests: LlmRequest[] = [];
    const p = fakeProvider('gemini:g', [{ chunks: [call('l', 'open_screen', { screen: 'home' }), end('tool_calls')] }], requests);
    const { events } = await run([p]);
    expect(requests.length).toBe(5);
    expect(events.at(-1)?.type).toBe('done');
  });
});

describe('failover — the chat never dies', () => {
  it('silently moves to the next model when the first is out of quota', async () => {
    const a = fakeProvider('gemini:a', [{ fail: new LlmError('rate_limit', 'quota') }]);
    const b = fakeProvider('groq:b', [{ chunks: [text('From B.'), end()] }]);
    const { events, router } = await run([a, b]);
    expect(shown(events)).toBe('From B.');
    expect(events.find((e) => e.type === 'source')).toMatchObject({ id: 'groq:b' });
    expect(events.some((e) => e.type === 'error' || e.type === 'reset')).toBe(false);
    expect(router.health()[0].healthy).toBe(false); // a is cooling down, so the next question skips it
  });

  it('resets the text and continues on the next model when one dies mid-answer', async () => {
    const a = fakeProvider('gemini:a', [{ chunks: [text('Half an ans')], fail: new LlmError('network', 'dropped') }]);
    const b = fakeProvider('groq:b', [{ chunks: [text('Full answer.'), end()] }]);
    const { events } = await run([a, b]);
    const i = events.findIndex((e) => e.type === 'reset');
    expect(i).toBeGreaterThan(-1);
    expect(shown(events.slice(i))).toBe('Full answer.');
  });

  it('keeps a model without function calling, feeding it the data in the prompt instead', async () => {
    const requests: LlmRequest[] = [];
    const p = fakeProvider('gemini:g', [{ fail: new LlmError('no_tools', 'no fn calling') }, { chunks: [text('Done.'), end()] }], requests);
    const { events, router } = await run([p]);
    expect(shown(events)).toBe('Done.');
    expect(requests[1].tools).toEqual([]);
    expect((requests[1].messages.at(-1) as { text: string }).text).toContain('<data>');
    expect(router.health()[0].mode).toBe('context');
  });

  it('puts the user-preferred model first', async () => {
    const a = fakeProvider('gemini:a', [{ chunks: [text('A'), end()] }]);
    const b = fakeProvider('groq:b', [{ chunks: [text('B'), end()] }]);
    const ctx = { ...base.context, prefs: { model: 'groq:b', disabledTools: [], style: 'balanced' as const, tone: 'friendly' as const, instructions: '' } };
    const { events } = await run([a, b], { context: ctx });
    expect(shown(events)).toBe('B');
  });

  it('answers from the built-in guide when every model fails — no error shown', async () => {
    const a = fakeProvider('gemini:a', [{ fail: new LlmError('server', 'down') }]);
    const b = fakeProvider('groq:b', [{ fail: new LlmError('rate_limit', 'quota') }]);
    const { events } = await run([a, b]);
    expect(events).toContainEqual({ type: 'source', id: 'offline', label: 'offline' });
    expect(shown(events).length).toBeGreaterThan(20);
    expect(events.some((e) => e.type === 'error')).toBe(false);
    expect(events.at(-1)?.type).toBe('done');
  });

  it('works offline when no provider key is configured at all', async () => {
    const { events } = await run([]);
    expect(events.some((e) => e.type === 'error')).toBe(false);
    expect(events.at(-1)?.type).toBe('done');
  });
});
