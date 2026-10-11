import type { ProviderRouter } from './router';
import { LlmError, type LlmImage } from './types';

/**
 * One non-streaming request with images through [router]. Throws AllProvidersFailed, or a timeout after
 * [timeoutMs] in total — so a serverless function always gets to record the failure before it is killed.
 */
export async function askWith(router: ProviderRouter, system: string, text: string, images: LlmImage[], maxTokens = 1800, timeoutMs = 40_000) {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), timeoutMs);
  const deadline = new Promise<never>((_, reject) =>
    ac.signal.addEventListener('abort', () => reject(new LlmError('timeout', `no answer within ${timeoutMs}ms`))),
  );
  const work = (async () => {
    let out = '';
    let model = '';
    const events = router.run(() => ({ system, messages: [{ role: 'user', text, images }], tools: [], maxTokens, temperature: 0.1 }), { signal: ac.signal });
    for await (const ev of events) {
      if (ev.type === 'source') model = ev.provider.label;
      else if (ev.type === 'reset') out = '';
      else if (ev.chunk.type === 'text') out += ev.chunk.text;
    }
    return { text: out, model };
  })();
  try {
    return await Promise.race([work, deadline]);
  } finally {
    clearTimeout(timer);
    ac.abort();
    work.catch(() => undefined);
    deadline.catch(() => undefined);
  }
}
