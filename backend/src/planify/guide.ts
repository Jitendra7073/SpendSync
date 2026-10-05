import type { MonthHistory } from './suggest';

/**
 * The AI planning guide. A language model reads the user's REAL numbers (facts below) and asks one
 * multiple-choice question at a time about something specific in them. The model never writes the plan:
 * each option carries a small, whitelisted "effect" (save 20%, trim Eating out by 15%...) that the phone applies
 * with its own arithmetic, so every rupee in the plan traces back to the user's history or a percentage they chose.
 */

export const MAX_QUESTIONS = 5;

export type GuideEffect =
  | { type: 'none' }
  | { type: 'savePercent'; value: number }
  | { type: 'keepFixed'; value: number } // 1 = keep regular bills, 0 = leave them out
  | { type: 'bufferPercent'; value: number }
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
  income: number;
  monthsUsed: number;
  regularBills: { category: string; perMonth: number }[];
  everyday: { category: string; perMonth: number[]; average: number; lastMonth: number; trendPercent: number }[];
  savingsPerMonth: number;
}

export type GuideStep = { done: false; question: GuideQuestion } | { done: true; note: string | null };

const SAVINGS_WORDS = ['saving', 'savings', 'investment', 'sip', 'emergency', 'fd', 'deposit'];
const round = (n: number) => Math.round(n);

/** What the model is allowed to know: totals per category per month, never individual transactions or merchants. */
export function buildFacts(history: MonthHistory[], income: number, fixed: Set<string>): GuideFacts {
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
  return { currency: 'INR', income: round(income), monthsUsed: history.length, regularBills, everyday: everyday.slice(0, 12), savingsPerMonth };
}

export function systemPrompt(language: string): string {
  return [
    'You help a person plan their monthly spending inside an expense-tracker app.',
    'You are given FACTS: their real numbers in rupees. Ask ONE multiple-choice question at a time that helps decide their limits.',
    'Rules:',
    '- Every question must be about something specific in FACTS (a category, a trend, a bill, their savings). Mention the real number when it helps, copied from FACTS. Never invent numbers.',
    '- Do not ask about a topic that already appears in TRANSCRIPT. Do not repeat or reword an earlier question.',
    '- Ask about what stands out: a category that rose a lot, the biggest everyday spend, regular bills, how much to save. Ask about savings exactly once, within the first three questions. The effect of each option must match its label and its topic (an Eating out question may only change Eating out; "keep as is" is {"type":"none"}).',
    `- Ask between 3 and ${MAX_QUESTIONS} questions in total, then finish.`,
    '- Give 3 or 4 short options (under 60 characters). Each option has an effect, one of:',
    '  {"type":"savePercent","value":0-40} | {"type":"keepFixed","value":1 or 0} | {"type":"bufferPercent","value":0-15} |',
    '  {"type":"categoryChange","category":"<exact category name from FACTS.everyday>","value":-50..30} | {"type":"none"}',
    '- "topic" is the category name, or "savings", "bills", "buffer".',
    `- Write question and option labels in ${language}. Keep category names as they appear in FACTS.`,
    'Reply with ONLY JSON, no markdown:',
    '{"done":false,"topic":"...","question":"...","options":[{"label":"...","effect":{...}}]}',
    'or, when you have enough: {"done":true,"note":"one friendly sentence on what you set up, using only numbers from FACTS"}',
  ].join('\n');
}

export function userPrompt(facts: GuideFacts, transcript: TranscriptItem[], focus?: string): string {
  const must = focus ? `
The next question MUST be about the topic "${focus}".` : '';
  return `${must}FACTS:\n${JSON.stringify(facts)}\n\nTRANSCRIPT (already asked and answered):\n${JSON.stringify(transcript)}\n\nReply with the next JSON.`;
}

const clamp = (n: unknown, lo: number, hi: number) => Math.min(hi, Math.max(lo, Math.round(Number(n))));

export function cleanEffect(raw: unknown, categories: Set<string>): GuideEffect {
  const e = (raw ?? {}) as Record<string, unknown>;
  switch (e.type) {
    case 'savePercent':
      return Number.isFinite(Number(e.value)) ? { type: 'savePercent', value: clamp(e.value, 0, 40) } : { type: 'none' };
    case 'keepFixed':
      return { type: 'keepFixed', value: Number(e.value) ? 1 : 0 };
    case 'bufferPercent':
      return Number.isFinite(Number(e.value)) ? { type: 'bufferPercent', value: clamp(e.value, 0, 15) } : { type: 'none' };
    case 'categoryChange':
      return typeof e.category === 'string' && categories.has(e.category) && Number.isFinite(Number(e.value))
        ? { type: 'categoryChange', category: e.category, value: clamp(e.value, -50, 30) }
        : { type: 'none' };
    default:
      return { type: 'none' };
  }
}

/** An option may only do what its question is about: a question on Eating out can only change Eating out. */
function fitToTopic(e: GuideEffect, topic: string, categories: Set<string>): GuideEffect {
  const t = topic.toLowerCase();
  if (t === 'savings') return e.type === 'savePercent' ? e : { type: 'none' };
  if (t === 'bills') return e.type === 'keepFixed' ? e : { type: 'none' };
  if (t === 'buffer') return e.type === 'bufferPercent' ? e : { type: 'none' };
  return e.type === 'categoryChange' && e.category.toLowerCase() === t && categories.has(topic) ? e : { type: 'none' };
}

/** Pulls the JSON object out of a model reply (they sometimes wrap it in text or fences) and checks it. Null = unusable. */
export function parseStep(text: string, categories: Set<string>, asked: TranscriptItem[]): GuideStep | null {
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
    if (label) options.push({ label, effect: fitToTopic(cleanEffect(r.effect, categories), topic, categories) });
  }
  if (options.length < 2) return null;
  return { done: false, question: { topic: topic || 'other', question, options } };
}
