import type { BucketKind } from './status';

/** One past month of the user's own money, already summed by the caller. */
export interface MonthHistory {
  month: string;
  /** Net spend per category (debits minus refunds) and how many transactions made it up. */
  byCategory: Record<string, { spent: number; count: number }>;
  /** Every credit of that month, in rupees. The biggest one is taken as the salary. */
  credits: number[];
}

export interface SuggestedItem {
  category: string;
  name: string;
  kind: BucketKind;
  limit: number;
  /** What the user spent on average, to show "last month ₹2,350" beside the new number. */
  average: number;
  sortOrder: number;
}

export interface Suggestion {
  items: SuggestedItem[];
  suggestedIncome: number;
  monthsUsed: number;
}

const SAVINGS_WORDS = ['saving', 'savings', 'investment', 'sip', 'emergency', 'fd', 'deposit'];

const roundTo = (n: number, step: number) => Math.max(step, Math.round(n / step) * step);
const median = (xs: number[]) => {
  if (xs.length === 0) return 0;
  const s = [...xs].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
};

/**
 * A first draft of next month's plan from the last few months: averages for everyday buckets, bills found by
 * repetition (same category every month, nearly the same amount, only a couple of payments), savings by name.
 * The user approves or edits it; nothing here is saved.
 */
export function suggestPlan(history: MonthHistory[]): Suggestion {
  const n = history.length;
  if (n === 0) return { items: [], suggestedIncome: 0, monthsUsed: 0 };

  const categories = new Set<string>();
  for (const h of history) for (const [c, v] of Object.entries(h.byCategory)) if (v.spent > 0) categories.add(c);

  const items: SuggestedItem[] = [];
  for (const category of categories) {
    const months = history.map((h) => h.byCategory[category]).filter((v): v is { spent: number; count: number } => !!v && v.spent > 0);
    // Seen in only one of several months: a one-off, not a habit.
    if (n >= 2 && months.length < 2) continue;

    const spends = months.map((m) => m.spent);
    const mean = spends.reduce((s, x) => s + x, 0) / spends.length;
    const steady = n >= 2 && months.length === n && spends.every((x) => Math.abs(x - mean) <= mean * 0.1);
    const fewPayments = months.every((m) => m.count <= 3);
    const isSavings = SAVINGS_WORDS.some((w) => category.toLowerCase().includes(w));

    let kind: BucketKind = 'spend';
    let limit = roundTo(mean, 50);
    if (isSavings) kind = 'savings';
    else if (steady && fewPayments && mean >= 1000) {
      kind = 'fixed';
      limit = roundTo(Math.max(...spends), 50);
    }
    items.push({ category, name: category, kind, limit, average: Math.round(mean), sortOrder: 0 });
  }

  const order: Record<BucketKind, number> = { fixed: 0, spend: 1, savings: 2 };
  items.sort((a, b) => order[a.kind] - order[b.kind] || b.limit - a.limit || a.category.localeCompare(b.category));
  items.forEach((it, i) => (it.sortOrder = i));

  const salaries = history.map((h) => (h.credits.length ? Math.max(...h.credits) : 0)).filter((x) => x > 0);
  return { items, suggestedIncome: salaries.length ? roundTo(median(salaries), 100) : 0, monthsUsed: n };
}
