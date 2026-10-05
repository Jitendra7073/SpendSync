import { sharedRouter } from '../loop';

/** One non-streaming question to the free-model chain; returns the text and which model answered. Throws AllProvidersFailed. */
export async function askModel(system: string, user: string, maxTokens = 900, temperature = 0.4): Promise<{ text: string; model: string }> {
  let text = '';
  let model = '';
  const events = sharedRouter().run(() => ({ system, messages: [{ role: 'user', text: user }], tools: [], maxTokens, temperature }));
  for await (const ev of events) {
    if (ev.type === 'source') model = ev.provider.label;
    else if (ev.type === 'reset') text = '';
    else if (ev.chunk.type === 'text') text += ev.chunk.text;
  }
  return { text, model };
}
