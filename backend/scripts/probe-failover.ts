/** Live failover check: first model is the flaky one, router must silently land on the next. Run: npx vite-node scripts/probe-failover.ts */
import { config } from 'dotenv';
import { buildProviders } from '../src/assistant/llm/chain';
import { ProviderRouter } from '../src/assistant/llm/router';

config({ path: '.env' });

async function main() {
  const router = new ProviderRouter(buildProviders({ ...process.env, ASSISTANT_CHAIN: 'gemini:gemma-4-31b-it,groq:openai/gpt-oss-120b' }));
  for (let i = 1; i <= 2; i++) {
    const t0 = Date.now();
    let text = '';
    let source = '';
    for await (const ev of router.run(() => ({ system: 'Answer in one short sentence.', messages: [{ role: 'user', text: 'What is 2+2?' }], tools: [], maxTokens: 60 }))) {
      if (ev.type === 'source') source = ev.provider.id;
      else if (ev.type === 'reset') text = '';
      else if (ev.chunk.type === 'text') text += ev.chunk.text;
    }
    console.log(`request ${i}: answered by ${source} in ${Date.now() - t0}ms -> "${text.trim().slice(0, 50)}"`);
  }
  console.log(router.health().map((h) => `${h.id} healthy=${h.healthy}`).join('\n'));
}
void main();
