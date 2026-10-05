import { describe, expect, it } from 'vitest';
import { buildFacts, cleanEffect, parseStep } from './guide';
import type { MonthHistory } from './suggest';

const h = (month: string, by: Record<string, number>): MonthHistory => ({
  month, credits: [40000], byCategory: Object.fromEntries(Object.entries(by).map(([c, spent]) => [c, { spent, count: 4 }])),
});
const history = [h('2026-07', { Rent: 12000, Food: 4000 }), h('2026-08', { Rent: 12000, Food: 4200 }), h('2026-09', { Rent: 12000, Food: 6000, SIP: 3000 })];

describe('guide facts', () => {
  const f = buildFacts(history, 40000, new Set(['Rent']));
  it('separates bills, everyday spend and savings, with trends from real numbers', () => {
    expect(f.regularBills).toEqual([{ category: 'Rent', perMonth: 12000 }]);
    expect(f.savingsPerMonth).toBe(3000);
    const food = f.everyday.find((e) => e.category === 'Food')!;
    expect(food.perMonth).toEqual([4000, 4200, 6000]);
    expect(food.trendPercent).toBe(corrected());
  });
});
function corrected() { return Math.round(((6000 - 4100) / 4100) * 100); }

describe('parsing the model reply', () => {
  const cats = new Set(['Food']);
  it('accepts a clean question and clamps wild effects', () => {
    const s = parseStep('Sure! {"done":false,"topic":"Food","question":"Food rose 46%. Cap it?","options":[{"label":"Yes","effect":{"type":"categoryChange","category":"Food","value":-90}},{"label":"No","effect":{"type":"none"}}]}', cats, []);
    expect(s).toMatchObject({ done: false });
    if (s && !s.done) expect(s.question.options[0].effect).toEqual({ type: 'categoryChange', category: 'Food', value: -50 });
  });
  it('rejects repeats, junk, unknown categories and too few options', () => {
    const ok = '{"done":false,"topic":"Food","question":"Q?","options":[{"label":"a","effect":{"type":"none"}},{"label":"b","effect":{"type":"none"}}]}';
    expect(parseStep(ok, cats, [{ topic: 'food', question: 'x', answer: 'y' }])).toBeNull();
    expect(parseStep('not json', cats, [])).toBeNull();
    expect(parseStep('{"done":false,"topic":"x","question":"Q?","options":[{"label":"only"}]}', cats, [])).toBeNull();
    expect(cleanEffect({ type: 'categoryChange', category: 'Invented', value: -10 }, cats)).toEqual({ type: 'none' });
    expect(cleanEffect({ type: 'savePercent', value: 99 }, cats)).toEqual({ type: 'savePercent', value: 40 });
  });
  it('understands done', () => {
    expect(parseStep('{"done":true,"note":"All set."}', cats, [])).toEqual({ done: true, note: 'All set.' });
  });
});
