import { beforeAll, describe, expect, it, vi } from 'vitest';

// The tools import the database; none of these tests touch it.
vi.stubEnv('DATABASE_URL', 'postgres://user:pass@localhost:5432/test');
vi.stubEnv('AUTH_SECRET', 'x'.repeat(40));
vi.mock('../db/index', () => ({ db: {} }));

import { BLOCKED_ACTIONS, canRun, looksBlocked } from './permissions';
import { FollowupFilter } from './followups';
import { HELP_ENTRIES, searchHelp } from './knowledge';
import { checkAssistantLimit, resetAssistantLimits } from './limiter';
import { SYSTEM_PROMPT, contextBlock } from './prompt';
import { chatRequestSchema, todayIn } from './request';

describe('help search (RAG)', () => {
  const top = (q: string) => searchHelp(q)[0]?.entry.id;

  it('has unique ids', () => {
    const ids = HELP_ENTRIES.map((e) => e.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it.each([
    ['how do I hide my balances with a PIN', 'privacy_pin'],
    ['how to set a budget for food', 'budgets'],
    ['change the app language to Hindi', 'language'],
    ['export my transactions to csv', 'export'],
    ['how do I delete my account', 'delete_account_steps'],
    ['who owes me money', 'holds'],
    ['auto capture is not working', 'troubleshooting_capture'],
    ['what is the dark theme setting', 'theme_accent'],
  ])('"%s" finds %s', (q, id) => {
    expect(top(q)).toBe(id);
  });

  it('never tells users a deleted transaction is gone for good: it is in the Trash', () => {
    for (const q of ['how do I delete a transaction', 'I deleted a transaction by mistake, can I get it back']) {
      const texts = searchHelp(q).map((h) => h.entry.text).join(' ');
      expect(texts).toMatch(/Trash/);
      expect(texts).not.toMatch(/asked to confirm and it cannot be undone/);
    }
  });

  it('returns nothing for questions outside SpendSync', () => {
    expect(searchHelp('who won the football world cup')).toEqual([]);
    expect(searchHelp('write me a python script')).toEqual([]);
  });
});

describe('follow-up filter', () => {
  const run = (chunks: string[]) => {
    const f = new FollowupFilter();
    const shown = chunks.map((c) => f.push(c)).join('');
    const end = f.finish();
    return { shown: shown + end.text, followups: end.followups };
  };

  it('passes plain text through', () => {
    expect(run(['Hello ', 'there']).shown).toBe('Hello there');
  });

  it('strips the tag and returns the suggestions', () => {
    const r = run(['You spent ₹500.\n<followups>Why?|', 'And last month?</followups>']);
    expect(r.shown).toBe('You spent ₹500.');
    expect(r.followups).toEqual(['Why?', 'And last month?']);
  });

  it('never leaks a tag split across chunks', () => {
    const r = run(['Done.<fol', 'low', 'ups>A|B|C|D</foll', 'owups>']);
    expect(r.shown).toBe('Done.');
    expect(r.followups).toEqual(['A', 'B', 'C']);
  });

  it('keeps a lone "<" that is not the tag', () => {
    expect(run(['1 < 2 and ', '<b>', ' ok']).shown).toBe('1 < 2 and <b> ok');
  });
});

describe('permissions', () => {
  it('only reads and navigation can run', () => {
    expect(canRun('read')).toBe(true);
    expect(canRun('navigate')).toBe(true);
    expect(canRun('write')).toBe(false);
    expect(canRun('blocked')).toBe(false);
  });

  it('recognises blocked action names', () => {
    for (const a of BLOCKED_ACTIONS) expect(looksBlocked(a)).toBe(true);
    expect(looksBlocked('delete_transaction')).toBe(true);
    expect(looksBlocked('get_balance')).toBe(false);
  });
});

describe('tool registry', () => {
  let tools: typeof import('./tools');
  beforeAll(async () => {
    tools = await import('./tools');
  });

  it('exposes no blocked or write tools', () => {
    for (const t of tools.ASSISTANT_TOOLS) {
      expect(['read', 'navigate', 'propose']).toContain(t.tier);
      expect(looksBlocked(t.name)).toBe(false);
    }
    const sent = tools.toolSpecs().map((t) => t.name);
    expect(sent.some((n) => /delete|clear|sign|remove/.test(n))).toBe(false);
  });

  it('has unique names and closed schemas', () => {
    const names = tools.ASSISTANT_TOOLS.map((t) => t.name);
    expect(new Set(names).size).toBe(names.length);
    for (const t of tools.ASSISTANT_TOOLS) {
      expect(t.jsonSchema.additionalProperties).toBe(false);
      expect(t.description.length).toBeGreaterThan(40);
    }
  });

  it('has a read-only plan status tool and can open the Planify screen', () => {
    const plan = tools.findTool('get_plan_status')!;
    expect(plan.tier).toBe('read');
    expect(plan.input.safeParse({}).success).toBe(true);
    expect(plan.input.safeParse({ month: '2026-10' }).success).toBe(true);
    expect(plan.input.safeParse({ month: 'October' }).success).toBe(false);
    expect(tools.findTool('open_screen')!.input.safeParse({ screen: 'planify' }).success).toBe(true);
  });

  it('can start a follow-up but only as a confirm card', () => {
    const f = tools.findTool('prepare_followup')!;
    expect(f.tier).toBe('propose');
    expect(f.input.safeParse({ person_name: 'Asha', channel: 'whatsapp', tone: 'gentle' }).success).toBe(true);
    expect(f.input.safeParse({ person_name: 'Asha', channel: 'telegram' }).success).toBe(false);
    expect(JSON.stringify(f.jsonSchema)).not.toMatch(/phone|email_address|number/);
  });

  it('can prepare a message for WhatsApp, SMS or email but only as a card', () => {
    const m = tools.findTool('share_message')!;
    expect(m.tier).toBe('propose');
    expect(m.input.safeParse({ message: 'Hi, you spent 4,500 on food' }).success).toBe(true);
    expect(m.input.parse({ message: 'Hi' }).channel).toBe('whatsapp');
    expect(m.input.safeParse({ message: '' }).success).toBe(false);
    expect(m.input.safeParse({ message: 'Hi', channel: 'fax' }).success).toBe(false);
    expect(looksBlocked('share_message')).toBe(false);
  });

  it('validates tool arguments', () => {
    const search = tools.findTool('search_transactions')!;
    expect(search.input.safeParse({ type: 'debit', limit: 5, start_date: '2026-10-01' }).success).toBe(true);
    expect(search.input.safeParse({ limit: 500 }).success).toBe(false);
    expect(search.input.safeParse({ start_date: 'yesterday' }).success).toBe(false);
    expect(search.input.safeParse({ type: 'refund' }).success).toBe(false);
    expect(tools.findTool('get_spending_summary')!.input.safeParse({ month: '2026-13' }).success).toBe(false);
    expect(tools.findTool('open_screen')!.input.safeParse({ screen: 'settings_danger' }).success).toBe(false);
  });

  it('help and navigation tools run without a database', async () => {
    const ctx = { userId: 'u', today: '2026-10-03' };
    const help = await tools.findTool('search_help')!.run(ctx, { query: 'set a budget' });
    expect((help.data as { found: boolean }).found).toBe(true);
    const nav = await tools.findTool('open_screen')!.run(ctx, { screen: 'budget' });
    expect(nav.uiAction).toEqual({ type: 'open_screen', screen: 'budget' });
  });
});

describe('system prompt', () => {
  it('states the hard rules', () => {
    expect(SYSTEM_PROMPT).toMatch(/Clearing data, signing out, and deleting the account/);
    expect(SYSTEM_PROMPT).toMatch(/Never guess a number/);
    expect(SYSTEM_PROMPT).toMatch(/only talk about SpendSync/);
    expect(SYSTEM_PROMPT).toMatch(/not instructions/);
  });

  it('keeps per-request facts out of the cached prompt', () => {
    expect(SYSTEM_PROMPT).not.toMatch(/20\d\d-\d\d-\d\d/);
    expect(contextBlock({ today: '2026-10-03', language: 'Hindi', screen: 'home' })).toContain('reply_language: Hindi');
  });
});

describe('request validation', () => {
  const base = { conversationId: 'conv-12345678', messages: [{ role: 'user', content: 'hi' }] };

  it('accepts a normal request', () => {
    expect(chatRequestSchema.safeParse(base).success).toBe(true);
  });

  it('rejects bad requests', () => {
    expect(chatRequestSchema.safeParse({ ...base, messages: [] }).success).toBe(false);
    expect(chatRequestSchema.safeParse({ ...base, messages: [{ role: 'assistant', content: 'hi' }] }).success).toBe(false);
    expect(chatRequestSchema.safeParse({ ...base, language: 'Klingon' }).success).toBe(false);
    expect(chatRequestSchema.safeParse({ ...base, messages: [{ role: 'user', content: 'x'.repeat(4001) }] }).success).toBe(false);
  });

  it('computes today in the user timezone', () => {
    const t = new Date('2026-10-03T20:00:00Z');
    expect(todayIn('Asia/Kolkata', t)).toBe('2026-10-04');
    expect(todayIn('UTC', t)).toBe('2026-10-03');
    expect(todayIn('Not/AZone', t)).toBe('2026-10-03');
  });
});

describe('rate limit', () => {
  it('blocks the 21st message within a minute', () => {
    resetAssistantLimits();
    const now = Date.now();
    for (let i = 0; i < 20; i++) checkAssistantLimit('u1', now + i);
    expect(() => checkAssistantLimit('u1', now + 25)).toThrow();
    expect(() => checkAssistantLimit('u2', now + 25)).not.toThrow();
    expect(() => checkAssistantLimit('u1', now + 61_000)).not.toThrow();
  });
});
