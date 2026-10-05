/** Asks the real free models to run the planning guide on made-up numbers. Usage: npx vite-node scripts/probe-guide.ts [Language] */
import 'dotenv/config';
import { sharedRouter } from '../src/assistant/loop';
import { allowedIncomes, buildFacts, parseStep, systemPrompt, userPrompt, type TranscriptItem } from '../src/planify/guide';
import { guessIncome, type CreditRow } from '../src/planify/income';
import type { MonthHistory } from '../src/planify/suggest';

const h = (month: string, by: Record<string, number>): MonthHistory => ({
  month, credits: [60000], byCategory: Object.fromEntries(Object.entries(by).map(([c, spent]) => [c, { spent, count: 5 }])),
});
const history = [
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
const facts = buildFacts(history, 8000, new Set(['Rent']), credits, guess); // the user had typed 8,000
const incomes = allowedIncomes(facts);
const cats = new Set(facts.everyday.map((e) => e.category));
const language = process.argv[2] ?? 'English';

async function ask(transcript: TranscriptItem[]) {
  let text = '';
  const t0 = Date.now();
  let model = '';
  for await (const ev of sharedRouter().run(() => ({ system: systemPrompt(language), messages: [{ role: 'user', text: userPrompt(facts, transcript) }], tools: [], maxTokens: 900, temperature: 0.4 }))) {
    if (ev.type === 'source') model = ev.provider.label;
    else if (ev.type === 'reset') text = '';
    else if (ev.chunk.type === 'text') text += ev.chunk.text;
  }
  return { step: parseStep(text, cats, transcript, incomes, transcript.length === 0), ms: Date.now() - t0, model, text };
}

(async () => {
  const transcript: TranscriptItem[] = [];
  for (let i = 0; i < 7; i++) {
    const { step, ms, model, text } = await ask(transcript);
    if (!step) { console.log('UNUSABLE', ms + 'ms', model, text); break; }
    if (step.done) { console.log(`DONE (${ms}ms, ${model}):`, step.note); break; }
    console.log(`\nQ${i + 1} [${step.question.topic}] ${ms}ms ${model}\n  ${step.question.question}`);
    step.question.options.forEach((o) => console.log(`   - ${o.label}  ${JSON.stringify(o.effect)}`));
    transcript.push({ topic: step.question.topic, question: step.question.question, answer: step.question.options[0].label });
  }
})();
