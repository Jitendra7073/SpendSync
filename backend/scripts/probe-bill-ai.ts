/** Sends a receipt image to every vision model and reports which ones read it. Usage: npx vite-node scripts/probe-bill-ai.ts path/to/receipt.jpg */
import { readFileSync } from 'node:fs';
import { buildVisionProviders } from '../src/assistant/llm/chain';
import { ProviderRouter } from '../src/assistant/llm/router';
import { askWith } from '../src/assistant/llm/vision';
import { parseBillRead } from '../src/billai/parse';
import { buildBillPrompt, BILL_USER_TEXT } from '../src/billai/prompt';

const file = process.argv[2];
if (!file) throw new Error('Pass an image path');
const image = { mime: 'image/jpeg' as const, base64: readFileSync(file).toString('base64') };
for (const p of buildVisionProviders()) {
  const t0 = Date.now();
  try {
    const { text } = await askWith(new ProviderRouter([p], { firstTokenMs: 40_000, idleMs: 40_000 }), buildBillPrompt({ language: 'English', style: 'balanced', tone: 'friendly' }), BILL_USER_TEXT, [image]);
    const r = parseBillRead(text);
    console.log(`OK   ${p.label} ${Date.now() - t0}ms total=${r.total} items=${r.items.length} conf=${r.confidence}`);
  } catch (e) {
    console.log(`FAIL ${p.label} ${Date.now() - t0}ms ${e instanceof Error ? e.message.slice(0, 120) : e}`);
  }
}
