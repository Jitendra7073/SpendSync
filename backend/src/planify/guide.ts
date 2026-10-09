import { looksLikeSalary, type CreditRow, type IncomeGuess } from './income';
import type { MonthHistory } from './suggest';

/**
 * The AI planning guide. A language model reads the user's REAL numbers (facts below) and asks one
 * multiple-choice question at a time about something specific in them. The model never writes the plan:
 * each option carries a small, whitelisted "effect" (save 20%, trim Eating out by 15%, a rent of the amount
 * the user types...) that the phone applies with its own arithmetic, so every rupee in the plan traces back to
 * the user's history, a percentage they picked, or an amount they typed.
 */

export type GuideEffect =
  | { type: 'none' }
  | { type: 'savePercent'; value: number }
  | { type: 'keepFixed'; value: number } // 1 = keep regular bills, 0 = leave them out
  | { type: 'bufferPercent'; value: number }
  | { type: 'setIncome'; value: number } // the money to plan with, one of the amounts found in the user's own credits
  | { type: 'incomeHaircut'; value: number } // plan on this many percent less than the income, because it varies
  | { type: 'goalMonthly'; name: string; value: number } // a named savings goal funded with this percent of income
  | { type: 'commitment'; name: string; value: number | null } // a regular bill not in the records; null = the user types the amount
  | { type: 'estimate'; category: string; value: number | null } // a monthly amount the user states for a category (little history)
  | { type: 'categoryChange'; category: string; value: number }; // percent change of one everyday category

export interface GuideOption {
  label: string;
  effect: GuideEffect;
}

export interface GuideQuestion {
  topic: string;
  question: string;
  options: GuideOption[];
}

export interface TranscriptItem {
  topic: string;
  question: string;
  answer: string;
}

export interface GuideFacts {
  currency: 'INR';
  monthsUsed: number;
  /** Under 2 months or very few categories: the guide asks more and the plan is labelled as a starting point. */
  lowHistory: boolean;
  avgMonthlySpend: number;
  regularBills: { category: string; perMonth: number }[];
  everyday: { category: string; perMonth: number[]; average: number; lastMonth: number; trendPercent: number }[];
  savingsPerMonth: number;
  /** What the records say about the money to plan: what was typed, and what the credits look like. */
  income: {
    entered: number;
    detected: { amount: number; label: string; source: IncomeGuess['source'] } | null;
    carryOver: { amount: number; label: string } | null;
    /** Salary-like credits per month differ by more than 15%; null when there are too few months to tell. */
    varies: boolean | null;
  };
  /** Recent credits as the user named them, so the model can tell a salary from a refund. */
  recentCredits: { month: string; amount: number; category: string; label: string }[];
}

export type GuideStep = { done: false; question: GuideQuestion } | { done: true; note: string | null };

const SAVINGS_WORDS = ['saving', 'savings', 'investment', 'sip', 'emergency', 'fd', 'deposit'];
const round = (n: number) => Math.round(n);

/** How many questions at most: more when there is little history to lean on. */
export const maxQuestions = (facts: GuideFacts) => (facts.lowHistory ? 9 : 6);

/** What the model is allowed to know: totals per category per month, never individual transactions or merchants. */
export function buildFacts(history: MonthHistory[], income: number, fixed: Set<string>, credits: CreditRow[] = [], guess?: IncomeGuess): GuideFacts {
  const categories = new Set<string>();
  for (const h of history) for (const [c, v] of Object.entries(h.byCategory)) if (v.spent > 0) categories.add(c);

  const regularBills: GuideFacts['regularBills'] = [];
  const everyday: GuideFacts['everyday'] = [];
  let savingsPerMonth = 0;
  for (const category of categories) {
    const perMonth = history.map((h) => round(h.byCategory[category]?.spent ?? 0));
    const seen = perMonth.filter((x) => x > 0);
    const average = round(seen.reduce((s, x) => s + x, 0) / Math.max(1, seen.length));
    if (SAVINGS_WORDS.some((w) => category.toLowerCase().includes(w))) { savingsPerMonth += average; continue; }
    if (fixed.has(category)) { regularBills.push({ category, perMonth: average }); continue; }
    const last = perMonth[perMonth.length - 1] ?? 0;
    const before = perMonth.slice(0, -1).filter((x) => x > 0);
    const beforeAvg = before.length ? before.reduce((s, x) => s + x, 0) / before.length : 0;
    everyday.push({
      category, perMonth, average, lastMonth: last,
      trendPercent: beforeAvg > 0 ? round(((last - beforeAvg) / beforeAvg) * 100) : 0,
    });
  }
  everyday.sort((a, b) => b.average - a.average);

  // Is the salary steady? Total of salary-like credits per earlier month.
  const byMonth = new Map<string, number>();
  for (const c of credits.filter(looksLikeSalary)) byMonth.set(c.month, (byMonth.get(c.month) ?? 0) + c.amount);
  const totals = [...byMonth.values()];
  const varies = totals.length >= 2 ? Math.max(...totals) > Math.min(...totals) * 1.15 : null;

  return {
    currency: 'INR', monthsUsed: history.length,
    lowHistory: history.length < 2 || regularBills.length + everyday.length < 3,
    avgMonthlySpend: round([...regularBills.map((b) => b.perMonth), ...everyday.map((e) => e.average)].reduce((s, x) => s + x, 0)),
    regularBills, everyday: everyday.slice(0, 12), savingsPerMonth,
    income: {
      entered: round(income),
      detected: guess && guess.income > 0 ? { amount: round(guess.income), label: guess.label, source: guess.source } : null,
      carryOver: guess && guess.carryOver > 0 ? { amount: round(guess.carryOver), label: guess.carryLabel } : null,
      varies,
    },
    recentCredits: [...credits].sort((a, b) => b.month.localeCompare(a.month) || b.amount - a.amount).slice(0, 15)
      .map((c) => ({ month: c.month, amount: round(c.amount), category: c.category, label: (c.merchant || c.note).slice(0, 40) })),
  };
}

/** The only amounts a "setIncome" option may carry: ones that really appear in the user's records or entry. */
export function allowedIncomes(facts: GuideFacts): Set<number> {
  const v = new Set<number>();
  if (facts.income.entered > 0) v.add(facts.income.entered);
  const d = facts.income.detected?.amount ?? 0;
  const c = facts.income.carryOver?.amount ?? 0;
  if (d > 0) { v.add(d); if (c > 0) v.add(d + c); }
  for (const r of facts.recentCredits) v.add(r.amount);
  return v;
}

export function systemPrompt(language: string, facts: GuideFacts): string {
  const max = maxQuestions(facts);
  const min = facts.lowHistory ? 6 : 4;
  return [
    'You are a highly intelligent financial coach helping a person plan their monthly spending in an app called SpendSync.',
    'Your goal is to build a highly personalized, trusted, and valuable plan by asking the MINIMUM number of insightful questions.',
    'You are given FACTS: their real financial data in rupees. Analyze these FACTS like a human expert to decide what matters most.',
    'Do NOT follow a rigid script. Instead, adapt your questions to their specific situation, finding anomalies, spikes, or missing information.',
    '',
    'General rules:',
    '- Ask ONE multiple-choice question at a time.',
    `- Ask between ${min} and ${max} questions in total, then finish.`,
    `- Write the question and every option label in ${language}. Keep category names as they appear in FACTS.`,
    '- Give 3 or 4 short options (under 60 characters). Each option has an effect that must match its label and its topic.',
    '- Do not ask about a topic that already appears in TRANSCRIPT. Do not repeat or reword an earlier question.',
    '- Use the TRANSCRIPT: build on earlier answers.',
    '',
    facts.lowHistory
      ? 'DISCOVERY MODE (New User / Low History): Since the user has little to no history, focus on establishing a baseline. Ask about their main everyday needs (e.g., Groceries, Transport, Eating out) using topic "estimate:<Category>". Provide options that are realistic ranges of monthly spend (using {"type":"estimate","category":"<Category>","value":<middle of range>}). Also, ask about their top financial goal (topic "goal") and any fixed commitments (topic "commitments"). Be welcoming and clear that these are just starting estimates.'
      : 'DEEP INSIGHT MODE (Existing User): Analyze their history to ask highly targeted questions. Look for: 1. Big changes or anomalies in specific categories (topic "<exact category name>", use {"type":"categoryChange","category":"<name>","value":-50..30}). 2. Inconsistent salary/income (topic "steadiness"). 3. High spending that could be optimized for savings (topic "savings"). Ask about what stands out the most.',
    '',
    'Mandatory checks:',
    '- If FACTS.income.detected exists and TRANSCRIPT is empty, your very first question MUST be about "income". Ask which amount to plan with. Options: {"type":"setIncome","value":<amount from FACTS>} for the detected amount, detected plus carry-over when there is one, and "a different amount" as {"type":"none"}.',
    '- If they haven\'t stated a savings or financial goal, consider asking about it (topic "goal" with {"type":"goalMonthly","name":"<name>","value":<percent>}).',
    '- If there might be missing regular bills, ask about them (topic "commitments" with {"type":"commitment","name":"<name>","value":null}).',
    '',
    'Effects allowed: setIncome, incomeHaircut, goalMonthly, commitment, estimate, categoryChange, savePercent, keepFixed (topic "bills"), bufferPercent, none.',
    'Reply with ONLY JSON, no markdown:',
    '{"done":false,"topic":"...","question":"...","options":[{"label":"...","effect":{...}}]}',
    'or, when you have enough insight: {"done":true,"note":"one friendly sentence on what you set up, using only numbers from FACTS or what the person told you"}'
  ].join('\n');
}

export function userPrompt(facts: GuideFacts, transcript: TranscriptItem[], focus?: string): string {
  const must = focus ? `\nThe next question MUST be about the topic "${focus}".\n` : '';
  return `${must}FACTS:\n${JSON.stringify(facts)}\n\nTRANSCRIPT (already asked and answered):\n${JSON.stringify(transcript)}\n\nReply with the next JSON.`;
}

const clamp = (n: unknown, lo: number, hi: number) => Math.min(hi, Math.max(lo, Math.round(Number(n))));
const nameOf = (v: unknown) => (typeof v === 'string' ? v.replace(/[\r\n\t]+/g, ' ').trim().slice(0, 30) : '');

export interface EffectLimits {
  categories: Set<string>;
  incomes: Set<number>;
  /** Income being planned: an estimate or bill can never exceed it. */
  income: number;
}

const NO_LIMITS: EffectLimits = { categories: new Set(), incomes: new Set(), income: 0 };

export function cleanEffect(raw: unknown, lim: EffectLimits = NO_LIMITS): GuideEffect {
  const e = (raw ?? {}) as Record<string, unknown>;
  const num = Number(e.value);
  switch (e.type) {
    case 'savePercent':
      return Number.isFinite(num) ? { type: 'savePercent', value: clamp(num, 0, 40) } : { type: 'none' };
    case 'keepFixed':
      return { type: 'keepFixed', value: num ? 1 : 0 };
    case 'bufferPercent':
      return Number.isFinite(num) ? { type: 'bufferPercent', value: clamp(num, 0, 15) } : { type: 'none' };
    case 'incomeHaircut':
      return Number.isFinite(num) ? { type: 'incomeHaircut', value: clamp(num, 0, 30) } : { type: 'none' };
    case 'goalMonthly': {
      const name = nameOf(e.name);
      return name && Number.isFinite(num) ? { type: 'goalMonthly', name, value: clamp(num, 2, 30) } : { type: 'none' };
    }
    case 'commitment': {
      const name = nameOf(e.name);
      if (!name) return { type: 'none' };
      if (e.value === null || e.value === undefined) return { type: 'commitment', name, value: null };
      return Number.isFinite(num) && num > 0 && (lim.income <= 0 || num <= lim.income) ? { type: 'commitment', name, value: round(num) } : { type: 'commitment', name, value: null };
    }
    case 'estimate': {
      const category = nameOf(e.category);
      if (!category) return { type: 'none' };
      if (e.value === null || e.value === undefined) return { type: 'estimate', category, value: null };
      return Number.isFinite(num) && num >= 0 && (lim.income <= 0 || num <= lim.income) ? { type: 'estimate', category, value: round(num) } : { type: 'none' };
    }
    case 'setIncome': {
      const v = Math.round(num);
      return Number.isFinite(v) && [...lim.incomes].some((a) => Math.abs(a - v) <= 1) ? { type: 'setIncome', value: v } : { type: 'none' };
    }
    case 'categoryChange':
      return typeof e.category === 'string' && lim.categories.has(e.category) && Number.isFinite(num)
        ? { type: 'categoryChange', category: e.category, value: clamp(num, -50, 30) }
        : { type: 'none' };
    default:
      return { type: 'none' };
  }
}

/** An option may only do what its question is about: a question on Eating out can only change Eating out. */
function fitToTopic(e: GuideEffect, topic: string, categories: Set<string>): GuideEffect {
  const t = topic.toLowerCase();
  if (t === 'income') return e.type === 'setIncome' ? e : { type: 'none' };
  if (t === 'goal') return e.type === 'goalMonthly' ? e : { type: 'none' };
  if (t === 'commitments') return e.type === 'commitment' ? e : { type: 'none' };
  if (t === 'steadiness') return e.type === 'incomeHaircut' ? e : { type: 'none' };
  if (t === 'savings') return e.type === 'savePercent' ? e : { type: 'none' };
  if (t === 'bills') return e.type === 'keepFixed' ? e : { type: 'none' };
  if (t === 'buffer') return e.type === 'bufferPercent' ? e : { type: 'none' };
  if (t.startsWith('estimate:')) return e.type === 'estimate' && e.category.toLowerCase() === t.slice('estimate:'.length).trim() ? e : { type: 'none' };
  return e.type === 'categoryChange' && e.category.toLowerCase() === t && categories.has(topic) ? e : { type: 'none' };
}

/** Pulls the JSON object out of a model reply (they sometimes wrap it in text or fences) and checks it. Null = unusable. */
export function parseStep(text: string, lim: EffectLimits, asked: TranscriptItem[], requireIncome = false): GuideStep | null {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  let j: Record<string, unknown>;
  try {
    j = JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
  if (j.done === true) {
    const note = typeof j.note === 'string' ? j.note.trim().slice(0, 300) : '';
    return { done: true, note: note || null };
  }
  const question = typeof j.question === 'string' ? j.question.trim().slice(0, 240) : '';
  const topic = typeof j.topic === 'string' ? j.topic.trim().slice(0, 60) : '';
  if (!question || !Array.isArray(j.options)) return null;
  if (topic && asked.some((a) => a.topic.toLowerCase() === topic.toLowerCase())) return null; // repeats an earlier topic
  const options: GuideOption[] = [];
  for (const o of j.options.slice(0, 4)) {
    const r = (o ?? {}) as Record<string, unknown>;
    const label = typeof r.label === 'string' ? r.label.trim().slice(0, 80) : '';
    if (label) options.push({ label, effect: fitToTopic(cleanEffect(r.effect, lim), topic, lim.categories) });
  }
  if (options.length < 2) return null;
  // The first question has to settle the income, with at least one real amount to choose.
  if (requireIncome && !(topic.toLowerCase() === 'income' && options.some((o) => o.effect.type === 'setIncome'))) return null;
  return { done: false, question: { topic: topic || 'other', question, options } };
}
