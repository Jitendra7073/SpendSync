import type { ProviderRouter } from './router';
import type { LlmImage } from './types';

/** One non-streaming request with images through [router]. Throws AllProvidersFailed. */
export async function askWith(router: ProviderRouter, system: string, text: string, images: LlmImage[], maxTokens = 1800) {
  let out = '';
  let model = '';
  const events = router.run(() => ({ system, messages: [{ role: 'user', text, images }], tools: [], maxTokens, temperature: 0.1 }));
  for await (const ev of events) {
    if (ev.type === 'source') model = ev.provider.label;
    else if (ev.type === 'reset') out = '';
    else if (ev.chunk.type === 'text') out += ev.chunk.text;
  }
  return { text: out, model };
}
