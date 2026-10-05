import { sharedRouter } from '../assistant/loop';
import { AllProvidersFailed } from '../assistant/llm/router';
import { buildFacts, MAX_QUESTIONS, parseStep, systemPrompt, userPrompt, type GuideStep, type TranscriptItem } from './guide';
import { loadHistory } from './service';
import { suggestPlan } from './suggest';

export type GuideReply = ({ source: 'ai'; model: string } & GuideStep) | { source: 'basic'; reason: string };

async function askModel(system: string, user: string): Promise<{ text: string; model: string }> {
  let text = '';
  let model = '';
  const events = sharedRouter().run(() => ({ system, messages: [{ role: 'user', text: user }], tools: [], maxTokens: 600, temperature: 0.4 }));
  for await (const ev of events) {
    if (ev.type === 'source') model = ev.provider.label;
    else if (ev.type === 'reset') text = '';
    else if (ev.chunk.type === 'text') text += ev.chunk.text;
  }
  return { text, model };
}

/**
 * Next step of the AI guide. Returns the model's next question (checked and cleaned), or "done", or
 * `basic` when no model could answer, in which case the phone falls back to its built-in questions.
 */
export async function guideNext(userId: string, input: { month: string; income: number; language: string; transcript: TranscriptItem[]; focus?: string }): Promise<GuideReply> {
  if (input.transcript.length >= MAX_QUESTIONS && !input.focus) return { source: 'ai', model: '', done: true, note: null };

  const history = await loadHistory(userId, input.month);
  if (history.length === 0) return { source: 'basic', reason: 'no history' };
  const fixed = new Set(suggestPlan(history).items.filter((i) => i.kind === 'fixed').map((i) => i.category));
  const facts = buildFacts(history, input.income, fixed);
  const categories = new Set(facts.everyday.map((e) => e.category));

  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      const { text, model } = await askModel(systemPrompt(input.language), userPrompt(facts, input.transcript, input.focus));
      const step = parseStep(text, categories, input.transcript);
      if (step) return { source: 'ai', model, ...step };
    }
    // Three or more answers are enough to build from, so a model hiccup then just ends the questions.
    if (input.transcript.length >= 3) return { source: 'ai', model: '', done: true, note: null };
    return { source: 'basic', reason: 'unusable model output' };
  } catch (e) {
    if (e instanceof AllProvidersFailed) {
      return input.transcript.length >= 3 ? { source: 'ai', model: '', done: true, note: null } : { source: 'basic', reason: 'no model available' };
    }
    throw e;
  }
}
