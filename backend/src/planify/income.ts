/**
 * Works out what the user's money to plan is from their own credits, instead of guessing from the biggest one:
 * a credit that looks like a salary (by category, merchant or note) this month wins; otherwise the salary of
 * earlier months; only then the largest credit. Money carried over from last month is found the same way.
 */
export interface CreditRow {
  month: string; // YYYY-MM
  amount: number;
  category: string;
  merchant: string;
  note: string;
}

export type IncomeSource = 'this_month' | 'history' | 'largest' | 'none';

export interface IncomeGuess {
  income: number;
  /** What the credit was called ("Enacton Salary"), so the user can see why this number. */
  label: string;
  source: IncomeSource;
  carryOver: number;
  carryLabel: string;
}

const SALARY = /\b(salary|payroll|stipend|wages?|ctc|pay\s?slip|paycheck|paycheque)\b/i;
const CARRY = /\b(savings?|saved|remaining|remainder|carry|carried|leftover|left\s?over|balance|forward|brought)\b/i;

const text = (c: CreditRow) => `${c.category} ${c.merchant} ${c.note}`;
export const looksLikeSalary = (c: CreditRow) => SALARY.test(text(c)) && !CARRY.test(c.category);
export const looksLikeCarry = (c: CreditRow) => CARRY.test(text(c)) && !SALARY.test(text(c));

const clean = (s: string) => s.trim().replace(/\s+/g, ' ');
const labelOf = (c: CreditRow) => clean(c.merchant) || clean(c.category);
const sum = (xs: number[]) => xs.reduce((s, x) => s + x, 0);
const roundTo = (n: number, step: number) => Math.round(n / step) * step;
const median = (xs: number[]) => {
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};
const biggest = (rows: CreditRow[]) => rows.reduce((a, b) => (b.amount > a.amount ? b : a));

export function guessIncome(credits: CreditRow[], month: string): IncomeGuess {
  const thisMonth = credits.filter((c) => c.month === month);
  const before = credits.filter((c) => c.month < month);

  const carryRows = thisMonth.filter(looksLikeCarry);
  const carry = carryRows.length ? { amount: sum(carryRows.map((c) => c.amount)), label: labelOf(biggest(carryRows)) } : { amount: 0, label: '' };
  const base = { carryOver: carry.amount, carryLabel: carry.label };

  const salaryNow = thisMonth.filter(looksLikeSalary);
  if (salaryNow.length) return { income: sum(salaryNow.map((c) => c.amount)), label: labelOf(biggest(salaryNow)), source: 'this_month', ...base };

  const byMonth = new Map<string, CreditRow[]>();
  for (const c of before.filter(looksLikeSalary)) byMonth.set(c.month, [...(byMonth.get(c.month) ?? []), c]);
  if (byMonth.size) {
    const totals = [...byMonth.values()].map((rows) => sum(rows.map((r) => r.amount)));
    const latest = [...byMonth.keys()].sort().pop()!;
    return { income: roundTo(median(totals), 100), label: labelOf(biggest(byMonth.get(latest)!)), source: 'history', ...base };
  }

  // Nothing is called a salary: fall back to the biggest credit of each earlier month (carry-overs excluded).
  const plain = before.filter((c) => !looksLikeCarry(c));
  const months = new Map<string, CreditRow>();
  for (const c of plain) if (!months.has(c.month) || c.amount > months.get(c.month)!.amount) months.set(c.month, c);
  if (months.size) {
    const rows = [...months.values()];
    return { income: roundTo(median(rows.map((r) => r.amount)), 100), label: labelOf(biggest(rows)), source: 'largest', ...base };
  }
  return { income: 0, label: '', source: 'none', ...base };
}
