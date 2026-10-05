import { askModel } from '../assistant/llm/ask';
import { AllProvidersFailed } from '../assistant/llm/router';
import { allowedIncomes, buildFacts, maxQuestions, parseStep, systemPrompt, userPrompt, type GuideStep, type TranscriptItem } from './guide';
import { guessIncome } from './income';
import { loadCredits, loadHistory } from './service';
import { suggestPlan } from './suggest';

export type GuideReply = ({ source: 'ai'; model: string } & GuideStep) | { source: 'basic'; reason: string };

/**
 * Next step of the AI guide. Returns the model's next question (checked and cleaned), or "done", or
 * `basic` when no model could answer, in which case the phone falls back to its built-in questions.
 * With little or no history the guide still works: it asks the person for estimates instead of reading them.
 */
export async function guideNext(userId: string, input: { month: string; income: number; language: string; transcript: TranscriptItem[]; focus?: string }): Promise<GuideReply> {
  const [history, credits] = await Promise.all([loadHistory(userId, input.month), loadCredits(userId, input.month)]);
  const fixed = new Set(suggestPlan(history).items.filter((i) => i.kind === 'fixed').map((i) => i.category));
  const guess = guessIncome(credits, input.month);
  const facts = buildFacts(history, input.income, fixed, credits, guess);
  if (input.transcript.length >= maxQuestions(facts) && !input.focus) return { source: 'ai', model: '', done: true, note: null };

  const limits = {
    categories: new Set(facts.everyday.map((e) => e.category)),
    incomes: allowedIncomes(facts),
    income: Math.max(input.income, guess.income + guess.carryOver),
  };
  const requireIncome = input.transcript.length === 0 && !input.focus && facts.income.detected !== null;
  // Enough answers to build a decent plan from; a model hiccup after this just ends the questions.
  const enough = input.transcript.length >= (facts.lowHistory ? 5 : 3);

  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      const { text, model } = await askModel(systemPrompt(input.language, facts), userPrompt(facts, input.transcript, input.focus));
      const step = parseStep(text, limits, input.transcript, requireIncome);
      if (step) return { source: 'ai', model, ...step };
    }
    return enough ? { source: 'ai', model: '', done: true, note: null } : { source: 'basic', reason: 'unusable model output' };
  } catch (e) {
    if (e instanceof AllProvidersFailed) return enough ? { source: 'ai', model: '', done: true, note: null } : { source: 'basic', reason: 'no model available' };
    throw e;
  }
}
