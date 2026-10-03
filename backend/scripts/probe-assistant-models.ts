/**
 * Live check of every configured assistant model: plain answer + tool call.
 * Run: npx tsx scripts/probe-assistant-models.ts   (reads backend/.env; never prints keys)
 */
import { config } from 'dotenv';
import { buildProviders } from '../src/assistant/llm/chain';
import type { LlmChunk, LlmProvider, LlmRequest } from '../src/assistant/llm/types';

config({ path: '.env' });

const tool = {
  name: 'get_balance',
  description: 'Returns the user net balance in rupees.',
  parameters: { type: 'object', properties: {}, additionalProperties: false },
};

async function run(p: LlmProvider, req: LlmRequest) {
  const chunks: LlmChunk[] = [];
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 30_000);
  try {
    for await (const c of p.stream({ ...req, signal: ctl.signal })) chunks.push(c);
    return { ok: true as const, chunks };
  } catch (e) {
    const err = e as { kind?: string; message?: string };
    return { ok: false as const, error: `${err.kind ?? 'error'}: ${(err.message ?? '').slice(0, 160)}` };
  } finally {
    clearTimeout(timer);
  }
}

async function main() {
  const providers = buildProviders();
  console.log(`${providers.length} models configured\n`);
  for (const p of providers) {
    const t0 = Date.now();
    const text = await run(p, {
      system: 'You are a helpful assistant. Answer in one short sentence.',
      messages: [{ role: 'user', text: 'Say hello and name the currency of India.' }],
      tools: [],
      maxTokens: 120,
    });
    const t1 = Date.now();
    const tools = await run(p, {
      system: 'Use tools to answer questions about the user. Never guess numbers.',
      messages: [{ role: 'user', text: 'What is my balance?' }],
      tools: [tool],
      maxTokens: 200,
    });
    const t2 = Date.now();

    const said = text.ok ? text.chunks.filter((c) => c.type === 'text').map((c) => (c as { text: string }).text).join('').trim().slice(0, 70) : '';
    const called = tools.ok && tools.chunks.some((c) => c.type === 'tool_call' && c.call.name === 'get_balance');
    console.log(p.id);
    console.log(`  text : ${text.ok ? `OK (${t1 - t0}ms) "${said}"` : `FAIL ${text.error}`}`);
    console.log(`  tools: ${tools.ok ? (called ? `OK (${t2 - t1}ms) called get_balance` : 'ran, but did not call the tool') : `FAIL ${tools.error}`}\n`);
  }
}

void main();
