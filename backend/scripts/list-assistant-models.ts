/** Lists model ids each configured provider currently offers (never prints keys). Run: npx vite-node scripts/list-assistant-models.ts */
import { config } from 'dotenv';

config({ path: '.env' });

async function get(url: string, headers: Record<string, string>) {
  const r = await fetch(url, { headers });
  return r.ok ? ((await r.json()) as unknown) : `HTTP ${r.status}`;
}

async function main() {
  const g = await get('https://generativelanguage.googleapis.com/v1beta/models?pageSize=200', { 'x-goog-api-key': process.env.GEMINI_API_KEY ?? '' });
  console.log('GEMINI', typeof g === 'string' ? g : (g as { models: Array<{ name: string; supportedGenerationMethods?: string[] }> }).models
    .filter((m) => m.supportedGenerationMethods?.includes('generateContent') && /gemma|flash/.test(m.name)).map((m) => m.name.replace('models/', '')).join(', '));

  const q = await get('https://api.groq.com/openai/v1/models', { authorization: `Bearer ${process.env.GROQ_API_KEY}` });
  console.log('GROQ', typeof q === 'string' ? q : (q as { data: Array<{ id: string }> }).data.map((m) => m.id).join(', '));

  const o = await get('https://openrouter.ai/api/v1/models', { authorization: `Bearer ${process.env.OPENROUTER_API_KEY}` });
  console.log('OPENROUTER free', typeof o === 'string' ? o : (o as { data: Array<{ id: string; supported_parameters?: string[] }> }).data
    .filter((m) => m.id.endsWith(':free') && m.supported_parameters?.includes('tools')).map((m) => m.id).join(', '));
}

void main();
