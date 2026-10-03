import { createHmac, randomInt, timingSafeEqual } from 'node:crypto';

/** Letters and digits that cannot be mistaken for each other (no I, O, 0, 1). */
const LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
const DIGITS = '23456789';
const ALL = LETTERS + DIGITS;

export const CODE_LENGTH = 6;
export const CODE_TTL_MS = 15 * 60 * 1000;
export const MAX_ATTEMPTS = 5;
export const MAX_REQUESTS_PER_HOUR = 3;

/** Six characters, always at least one letter and one digit, e.g. "K7M2QX". */
export function generateResetCode(pick: (max: number) => number = (n) => randomInt(n)): string {
  for (;;) {
    let code = '';
    for (let i = 0; i < CODE_LENGTH; i++) code += ALL[pick(ALL.length)];
    if (/[A-Z]/.test(code) && /[0-9]/.test(code)) return code;
  }
}

/** What the user typed -> the canonical form ("k7m 2qx" -> "K7M2QX"). */
export function normalizeCode(input: string): string {
  return input.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** Keyed hash of the code, bound to the email, so the stored value is useless on its own. */
export function hashCode(code: string, email: string, secret: string): string {
  return createHmac('sha256', secret).update(`${email.toLowerCase()}:${normalizeCode(code)}`).digest('hex');
}

function safeEqual(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

export interface ResetRecord {
  codeHash: string;
  attempts: number;
  expiresAt: Date;
  usedAt: Date | null;
}

export type Verdict = 'ok' | 'wrong' | 'expired' | 'locked' | 'none';

/** Pure decision: what should happen to this attempt. The caller does the database writes. */
export function judgeAttempt(record: ResetRecord | null, input: { code: string; email: string; secret: string; now: Date }): Verdict {
  if (!record || record.usedAt) return 'none';
  if (record.expiresAt.getTime() <= input.now.getTime()) return 'expired';
  if (record.attempts >= MAX_ATTEMPTS) return 'locked';
  return safeEqual(record.codeHash, hashCode(input.code, input.email, input.secret)) ? 'ok' : 'wrong';
}
