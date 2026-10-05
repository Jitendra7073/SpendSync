/** Asks the real free models to run the planning guide on made-up numbers. Usage: npx vite-node scripts/probe-guide.ts [rich|low] [Language] */
import 'dotenv/config';
import { sharedRouter } from '../src/assistant/loop';
import { allowedIncomes, buildFacts, maxQuestions, parseStep, systemPrompt, userPrompt, type TranscriptItem } from '../src/planify/guide';
import { guessIncome, type CreditRow } from '../src/planify/income';
import type { MonthHistory } from '../src/planify/suggest';

const h = (month: string, by: Record<string, number>): MonthHistory => ({
  month, credits: [60000], byCategory: Object.fromEntries(Object.entries(by).map(([c, spent]) => [c, { spent, count: 5 }])),
});
const scenario = process.argv[2] ?? 'rich';
const language = process.argv[3] ?? 'English';

const history: MonthHistory[] = scenario === 'low'
  ? [h('2026-09', { 'Eating out': 1800, Transport: 900 })]
  : [
      h('2026-07', { Rent: 15000, 'Eating out': 3000, Groceries: 6000, Transport: 2500, Shopping: 2000 }),
      h('2026-08', { Rent: 15000, 'Eating out': 3400, Groceries: 6200, Transport: 2400, Shopping: 2500 }),
      h('2026-09', { Rent: 15000, 'Eating out': 5200, Groceries: 6100, Transport: 2600, Shopping: 4800 }),
    ];
const credits: CreditRow[] = [
  { month: '2026-09', amount: 8000, category: 'Stable Record Management', merchant: 'Stable Account Records', note: '' },
  { month: '2026-10', amount: 21600, category: 'Salary', merchant: 'Enacton Salary', note: '' },
  { month: '2026-10', amount: 4045, category: 'Last Month Saving', merchant: 'last month remaining fund', note: '' },
];
const guess = guessIncome(credits, '2026-10');
const facts = buildFacts(history, 8000, new Set(scenario === 'low' ? [] : ['Rent']), credits, guess);
const lim = { categories: new Set(facts.everyday.map((e) => e.category)), incomes: allowedIncomes(facts), income: 25645 };
console.log(`scenario=${scenario} lowHistory=${facts.lowHistory} max=${maxQuestions(facts)}`);

async function ask(transcript: TranscriptItem[]) {
  let text = '';
  const t0 = Date.now();
  let model = '';
  for await (const ev of sharedRouter().run(() => ({ system: systemPrompt(language, facts), messages: [{ role: 'user', text: userPrompt(facts, transcript) }], tools: [], maxTokens: 900, temperature: 0.4 }))) {
    if (ev.type === 'source') model = ev.provider.label;
    else if (ev.type === 'reset') text = '';
    else if (ev.chunk.type === 'text') text += ev.chunk.text;
  }
  return { step: parseStep(text, lim, transcript, transcript.length === 0), ms: Date.now() - t0, model, text };
}

(async () => {
  const transcript: TranscriptItem[] = [];
  for (let i = 0; i < 10; i++) {
    const { step, ms, model, text } = await ask(transcript);
    if (!step) { console.log('UNUSABLE', ms + 'ms', model, text); break; }
    if (step.done) { console.log(`DONE (${ms}ms, ${model}):`, step.note); break; }
    console.log(`\nQ${i + 1} [${step.question.topic}] ${ms}ms ${model}\n  ${step.question.question}`);
    step.question.options.forEach((o) => console.log(`   - ${o.label}  ${JSON.stringify(o.effect)}`));
    // answer with the first option that actually does something, to exercise the follow-ups
    const pick = step.question.options.find((o) => o.effect.type !== 'none') ?? step.question.options[0];
    transcript.push({ topic: step.question.topic, question: step.question.question, answer: pick.label });
  }
})();
