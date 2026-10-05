import { describe, expect, it } from 'vitest';
import { guessIncome, type CreditRow } from './income';

const c = (month: string, amount: number, category: string, merchant = category, note = ''): CreditRow => ({ month, amount, category, merchant, note });

describe('guessIncome', () => {
  it('prefers this month\'s salary over an old unrelated credit, and finds the carry-over', () => {
    const g = guessIncome([
      c('2026-09', 8000, 'Stable Record Management', 'Stable Account Records'),
      c('2026-10', 21600, 'Salary', 'Enacton Salary '),
      c('2026-10', 4045, 'Last Month Saving', 'last month remaining fund'),
    ], '2026-10');
    expect(g).toMatchObject({ income: 21600, label: 'Enacton Salary', source: 'this_month', carryOver: 4045 });
  });

  it('uses earlier months\' salary when this month has none yet', () => {
    const g = guessIncome([c('2026-07', 30000, 'Salary'), c('2026-08', 30000, 'Salary'), c('2026-09', 5000, 'Refund')], '2026-10');
    expect(g).toMatchObject({ income: 30000, source: 'history' });
  });

  it('falls back to the biggest credit when nothing looks like a salary, ignoring carry-overs', () => {
    const g = guessIncome([c('2026-08', 8000, 'Freelance'), c('2026-09', 8200, 'Freelance'), c('2026-09', 900, 'Savings', 'carried forward')], '2026-10');
    expect(g).toMatchObject({ income: 8100, source: 'largest' });
  });

  it('adds several salary credits in the same month and handles no credits', () => {
    expect(guessIncome([c('2026-10', 20000, 'Salary'), c('2026-10', 5000, 'Other', 'second job payroll')], '2026-10').income).toBe(25000);
    expect(guessIncome([], '2026-10')).toMatchObject({ income: 0, source: 'none' });
  });
});
