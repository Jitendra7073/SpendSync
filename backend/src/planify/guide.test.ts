import { describe, expect, it } from 'vitest';
import { buildFacts, cleanEffect, maxQuestions, parseStep, type EffectLimits } from './guide';
import type { CreditRow } from './income';
import type { MonthHistory } from './suggest';

const h = (month: string, by: Record<string, number>): MonthHistory => ({
  month, credits: [40000], byCategory: Object.fromEntries(Object.entries(by).map(([c, spent]) => [c, { spent, count: 4 }])),
});
const history = [h('2026-07', { Rent: 12000, Food: 4000 }), h('2026-08', { Rent: 12000, Food: 4200 }), h('2026-09', { Rent: 12000, Food: 6000, SIP: 3000 })];
const lim = (over: Partial<EffectLimits> = {}): EffectLimits => ({ categories: new Set(['Food']), incomes: new Set([40000]), income: 40000, ...over });

describe('guide facts', () => {
  const f = buildFacts(history, 40000, new Set(['Rent']));
  it('separates bills, everyday spend and savings, with trends from real numbers', () => {
    expect(f.regularBills).toEqual([{ category: 'Rent', perMonth: 12000 }]);
    expect(f.savingsPerMonth).toBe(3000);
    const food = f.everyday.find((e) => e.category === 'Food')!;
    expect(food.perMonth).toEqual([4000, 4200, 6000]);
    expect(food.trendPercent).toBe(Math.round(((6000 - 4100) / 4100) * 100));
  });

  it('flags low history and asks more questions for it', () => {
    expect(f.lowHistory).toBe(true); // only Rent + Food: under three categories
    const rich = buildFacts([...history, h('2026-10', { Rent: 12000, Food: 4000, Fuel: 1500 })], 40000, new Set(['Rent']));
    expect(rich.lowHistory).toBe(false);
    expect(buildFacts([], 40000, new Set()).lowHistory).toBe(true);
    expect(maxQuestions(buildFacts([], 40000, new Set()))).toBeGreaterThan(maxQuestions(rich));
  });

  it('tells a steady salary from a varying one', () => {
    const c = (month: string, amount: number): CreditRow => ({ month, amount, category: 'Salary', merchant: 'Salary', note: '' });
    expect(buildFacts(history, 1, new Set(), [c('2026-08', 30000), c('2026-09', 30000)]).income.varies).toBe(false);
    expect(buildFacts(history, 1, new Set(), [c('2026-08', 20000), c('2026-09', 30000)]).income.varies).toBe(true);
    expect(buildFacts(history, 1, new Set(), [c('2026-09', 30000)]).income.varies).toBeNull();
  });
});

describe('effects', () => {
  it('clamps and validates every kind', () => {
    expect(cleanEffect({ type: 'savePercent', value: 99 }, lim())).toEqual({ type: 'savePercent', value: 40 });
    expect(cleanEffect({ type: 'incomeHaircut', value: 80 }, lim())).toEqual({ type: 'incomeHaircut', value: 30 });
    expect(cleanEffect({ type: 'goalMonthly', name: 'Emergency fund', value: 1 }, lim())).toEqual({ type: 'goalMonthly', name: 'Emergency fund', value: 2 });
    expect(cleanEffect({ type: 'goalMonthly', name: '  ', value: 10 }, lim())).toEqual({ type: 'none' });
    expect(cleanEffect({ type: 'categoryChange', category: 'Invented', value: -10 }, lim())).toEqual({ type: 'none' });
    expect(cleanEffect({ type: 'setIncome', value: 12345 }, lim())).toEqual({ type: 'none' }); // not in the records
    expect(cleanEffect({ type: 'setIncome', value: 40000 }, lim())).toEqual({ type: 'setIncome', value: 40000 });
  });

  it('lets the person type a bill amount or an estimate, but never more than the income', () => {
    expect(cleanEffect({ type: 'commitment', name: 'Rent', value: null }, lim())).toEqual({ type: 'commitment', name: 'Rent', value: null });
    expect(cleanEffect({ type: 'commitment', name: 'Rent', value: 9e9 }, lim())).toEqual({ type: 'commitment', name: 'Rent', value: null });
    expect(cleanEffect({ type: 'estimate', category: 'Groceries', value: 5000 }, lim())).toEqual({ type: 'estimate', category: 'Groceries', value: 5000 });
    expect(cleanEffect({ type: 'estimate', category: 'Groceries', value: 9e9 }, lim())).toEqual({ type: 'none' });
  });
});

describe('parsing the model reply', () => {
  const q = (topic: string, effect: string) =>
    `{"done":false,"topic":"${topic}","question":"Q?","options":[{"label":"a","effect":${effect}},{"label":"b","effect":{"type":"none"}}]}`;

  it('accepts a clean question and clamps wild effects', () => {
    const s = parseStep('Sure! {"done":false,"topic":"Food","question":"Food rose 46%. Cap it?","options":[{"label":"Yes","effect":{"type":"categoryChange","category":"Food","value":-90}},{"label":"No","effect":{"type":"none"}}]}', lim(), []);
    expect(s).toMatchObject({ done: false });
    if (s && !s.done) expect(s.question.options[0].effect).toEqual({ type: 'categoryChange', category: 'Food', value: -50 });
  });

  it('only lets an option do what its topic is about', () => {
    const wrong = parseStep(q('goal', '{"type":"savePercent","value":10}'), lim(), []);
    if (wrong && !wrong.done) expect(wrong.question.options[0].effect).toEqual({ type: 'none' });
    const right = parseStep(q('estimate:Groceries', '{"type":"estimate","category":"Groceries","value":4000}'), lim(), []);
    if (right && !right.done) expect(right.question.options[0].effect).toEqual({ type: 'estimate', category: 'Groceries', value: 4000 });
    const other = parseStep(q('estimate:Groceries', '{"type":"estimate","category":"Transport","value":4000}'), lim(), []);
    if (other && !other.done) expect(other.question.options[0].effect).toEqual({ type: 'none' });
  });

  it('rejects repeats, junk, too few options and a first question that skips the income', () => {
    const ok = q('Food', '{"type":"none"}');
    expect(parseStep(ok, lim(), [{ topic: 'food', question: 'x', answer: 'y' }])).toBeNull();
    expect(parseStep('not json', lim(), [])).toBeNull();
    expect(parseStep('{"done":false,"topic":"x","question":"Q?","options":[{"label":"only"}]}', lim(), [])).toBeNull();
    expect(parseStep(q('savings', '{"type":"savePercent","value":10}'), lim(), [], true)).toBeNull();
    expect(parseStep(q('income', '{"type":"setIncome","value":40000}'), lim(), [], true)).not.toBeNull();
  });

  it('understands done', () => {
    expect(parseStep('{"done":true,"note":"All set."}', lim(), [])).toEqual({ done: true, note: 'All set.' });
  });
});
