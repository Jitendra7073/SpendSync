import { anthropicProvider } from './anthropic';
import { geminiProvider } from './gemini';
import { openAiCompatProvider } from './openai-compat';
import type { LlmProvider } from './types';

export interface ChainEntry {
  provider: 'gemini' | 'groq' | 'cerebras' | 'openrouter' | 'mistral' | 'anthropic';
  model: string;
  label: string;
}

/**
 * The default failover order, best free model first. Every entry only becomes active when its
 * provider's API key is set, so you can start with ONE free key and add more over time.
 *
 * Free-tier limits and model names change; override the whole list without a code change with
 *   ASSISTANT_CHAIN="gemini:gemma-4-31b-it,groq:llama-3.3-70b-versatile,..."
 */
export const DEFAULT_CHAIN: ChainEntry[] = [
  // Checked live against the real APIs (Oct 2026). Gemma 26B is steady; 31B is sometimes slow or errors, so it comes second.
  { provider: 'gemini', model: 'gemma-4-26b-a4b-it', label: 'Gemma 4 26B · Google' },
  { provider: 'gemini', model: 'gemma-4-31b-it', label: 'Gemma 4 31B · Google' },
  { provider: 'gemini', model: 'gemini-2.5-flash-lite', label: 'Gemini 2.5 Flash-Lite · Google' },
  { provider: 'groq', model: 'openai/gpt-oss-120b', label: 'GPT-OSS 120B · Groq' },
  { provider: 'groq', model: 'qwen/qwen3.8-27b', label: 'Qwen 3.8 27B · Groq' },
  { provider: 'openrouter', model: 'google/gemma-4-26b-a4b-it:free', label: 'Gemma 4 26B · OpenRouter' },
  { provider: 'openrouter', model: 'nvidia/nemotron-3-super-120b-a12b:free', label: 'Nemotron 3 Super · OpenRouter' },
  // Not checked yet (no key at the time of writing); a wrong name just parks the entry.
  { provider: 'cerebras', model: 'llama-3.3-70b', label: 'Llama 3.3 70B · Cerebras' },
  { provider: 'mistral', model: 'mistral-small-latest', label: 'Mistral Small · Mistral' },
  // Smallest and fastest, with a big free allowance: the last AI safety net.
  { provider: 'groq', model: 'openai/gpt-oss-20b', label: 'GPT-OSS 20B · Groq' },
];

const PAID_CHAIN: ChainEntry[] = [
  { provider: 'anthropic', model: 'claude-haiku-4-5', label: 'Claude Haiku 4.5 · Anthropic (paid)' },
];

const PROVIDER_NAMES: Record<string, string> = {
  gemini: 'Google',
  groq: 'Groq',
  cerebras: 'Cerebras',
  openrouter: 'OpenRouter',
  mistral: 'Mistral',
  anthropic: 'Anthropic',
};

const OPENAI_BASES: Record<string, string> = {
  groq: 'https://api.groq.com/openai/v1',
  cerebras: 'https://api.cerebras.ai/v1',
  openrouter: 'https://openrouter.ai/api/v1',
  mistral: 'https://api.mistral.ai/v1',
};

const KEY_ENV: Record<string, string[]> = {
  gemini: ['GEMINI_API_KEY', 'GOOGLE_API_KEY'],
  groq: ['GROQ_API_KEY'],
  cerebras: ['CEREBRAS_API_KEY'],
  openrouter: ['OPENROUTER_API_KEY'],
  mistral: ['MISTRAL_API_KEY'],
  anthropic: ['ANTHROPIC_API_KEY'],
};

type Env = Record<string, string | undefined>;

export function parseChain(spec: string | undefined): ChainEntry[] {
  if (!spec?.trim()) return DEFAULT_CHAIN;
  const out: ChainEntry[] = [];
  for (const raw of spec.split(',')) {
    const item = raw.trim();
    const at = item.indexOf(':');
    if (at < 1) continue;
    const provider = item.slice(0, at) as ChainEntry['provider'];
    const model = item.slice(at + 1).trim();
    if (!(provider in PROVIDER_NAMES) || !model) continue;
    const known = [...DEFAULT_CHAIN, ...PAID_CHAIN].find((e) => e.provider === provider && e.model === model);
    out.push(known ?? { provider, model, label: `${model} · ${PROVIDER_NAMES[provider]}` });
  }
  return out.length ? out : DEFAULT_CHAIN;
}

function keyFor(provider: string, env: Env): string | undefined {
  for (const name of KEY_ENV[provider] ?? []) if (env[name]) return env[name];
  return undefined;
}

/** Providers that are usable right now: key present (paid ones also need an explicit opt-in). */
export function buildProviders(env: Env = process.env, fetchFn?: typeof fetch): LlmProvider[] {
  const allowPaid = env.ASSISTANT_ALLOW_PAID === 'true';
  const entries = [...parseChain(env.ASSISTANT_CHAIN), ...(allowPaid ? PAID_CHAIN : [])];
  const seen = new Set<string>();
  const out: LlmProvider[] = [];
  for (const e of entries) {
    const id = `${e.provider}:${e.model}`;
    if (seen.has(id)) continue;
    if (e.provider === 'anthropic' && !allowPaid) continue;
    const apiKey = keyFor(e.provider, env);
    if (!apiKey) continue;
    seen.add(id);
    if (e.provider === 'gemini') out.push(geminiProvider({ apiKey, model: e.model, label: e.label, fetchFn }));
    else if (e.provider === 'anthropic') out.push(anthropicProvider({ model: e.model, label: e.label }));
    else {
      out.push(
        openAiCompatProvider({
          provider: e.provider,
          model: e.model,
          label: e.label,
          baseUrl: OPENAI_BASES[e.provider],
          apiKey,
          extraHeaders: e.provider === 'openrouter' ? { 'HTTP-Referer': 'https://spendsync.app', 'X-Title': 'SpendSync' } : undefined,
          fetchFn,
        }),
      );
    }
  }
  return out;
}
