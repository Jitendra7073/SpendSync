import { describe, expect, it, vi } from 'vitest';

vi.stubEnv('DATABASE_URL', 'postgres://user:pass@localhost:5432/test');
vi.stubEnv('AUTH_SECRET', 'x'.repeat(40));
vi.mock('../db/index', () => ({ db: {} }));

import { ToolTagFilter } from './tagfilter';
import { findTool } from './tools';

function run(chunks: string[]) {
  const f = new ToolTagFilter();
  let text = '';
  const screens: string[] = [];
  for (const c of [...chunks]) {
    const r = f.push(c);
    text += r.text;
    screens.push(...r.screens);
  }
  const end = f.finish();
  return { text: text + end.text, screens };
}

describe('tool tag filter', () => {
  it('removes a fake open_screen tag, even split across chunks, and turns it into a button', () => {
    const r = run(['Done. <open_', 'screen screen="budget"', '/> Bye']);
    expect(r.text).toBe('Done.  Bye');
    expect(r.screens).toEqual(['budget']);
  });

  it('ignores an unknown screen name instead of showing it', () => {
    const r = run(['Ok <open_screen screen="add_transition"/>']);
    expect(r.text.trim()).toBe('Ok');
    expect(r.screens).toEqual([]);
  });

  it('drops think blocks and keeps followups and normal "<" text', () => {
    const r = run(['<think>secret plan</think>Hi 3 < 5', '\n<followups>A?|B?</followups>']);
    expect(r.text).toBe('Hi 3 < 5\n<followups>A?|B?</followups>');
  });
});

describe('propose_entry', () => {
  const ctx = { userId: 'u', today: '2026-10-03' };
  const tool = findTool('propose_entry')!;

  it('only prepares a card (never saves) and is a runnable, non-write tier', async () => {
    expect(tool.tier).toBe('propose');
    const parsed = tool.input.parse({ kind: 'expense', amount: 200, category: 'Other', person_name: 'Uttam', return_date: '2026-10-05' });
    const out = await tool.run(ctx, parsed);
    expect(out.uiAction).toMatchObject({ type: 'propose_entry', entry: { kind: 'expense', amount: 200, person: 'Uttam', returnDate: '2026-10-05', date: '2026-10-03' } });
    expect((out.data as { saved: boolean }).saved).toBe(false);
  });

  it('rejects a person without a return date, a past return date, and a future entry date', async () => {
    expect(tool.input.safeParse({ kind: 'expense', amount: 5, category: 'Food', person_name: 'A' }).success).toBe(false);
    await expect(tool.run(ctx, tool.input.parse({ kind: 'expense', amount: 5, category: 'Food', person_name: 'A', return_date: '2026-10-01' }))).rejects.toThrow();
    await expect(tool.run(ctx, tool.input.parse({ kind: 'income', amount: 5, category: 'Gift', date: '2026-10-09' }))).rejects.toThrow();
  });
});
