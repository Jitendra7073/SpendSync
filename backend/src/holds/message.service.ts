import { askModel } from '../assistant/llm/ask';
import { AllProvidersFailed } from '../assistant/llm/router';
import { checkDraft, systemPrompt, userPrompt, type MessageRequest } from './message';

export type MessageReply = { source: 'ai'; text: string; model: string } | { source: 'none'; reason: string };

/** A checked AI draft, or `none` so the phone writes it from its own translated template instead. */
export async function draftMessage(r: MessageRequest): Promise<MessageReply> {
  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      const { text, model } = await askModel(systemPrompt(r), userPrompt(r), 500, 0.7);
      const ok = checkDraft(text, r);
      if (ok) return { source: 'ai', text: ok, model };
    }
    return { source: 'none', reason: 'draft failed the checks' };
  } catch (e) {
    if (e instanceof AllProvidersFailed) return { source: 'none', reason: 'no model available' };
    throw e;
  }
}
