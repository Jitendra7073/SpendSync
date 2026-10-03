import { ApiError } from '../utils/errors';

/**
 * Per-user cap on assistant messages (the generic limiter is per-IP and shared by every endpoint).
 * In-memory, per server instance — enough to stop a runaway client; move to Redis if the API is
 * ever scaled across many instances and a hard global cap is needed.
 */
const WINDOWS = [
  { ms: 60_000, max: 20, label: 'minute' },
  { ms: 24 * 3_600_000, max: 300, label: 'day' },
] as const;

const hits = new Map<string, number[]>();

export function checkAssistantLimit(userId: string, now = Date.now()): void {
  const list = (hits.get(userId) ?? []).filter((t) => now - t < WINDOWS[1].ms);
  for (const w of WINDOWS) {
    if (list.filter((t) => now - t < w.ms).length >= w.max) {
      hits.set(userId, list);
      throw new ApiError(429, `Assistant limit reached for this ${w.label}`, 'RATE_LIMIT_EXCEEDED');
    }
  }
  list.push(now);
  hits.set(userId, list);
}

/** Test helper. */
export function resetAssistantLimits(): void {
  hits.clear();
}
