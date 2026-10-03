import { hashPassword } from 'better-auth/crypto';
import { and, count, desc, eq, gt, isNull } from 'drizzle-orm';
import { config } from '../config/env';
import { db } from '../db/index';
import { account, passwordResets, session, user } from '../db/schema/index';
import { sendEmail } from '../lib/email';
import { logger } from '../utils/logger';
import { CODE_TTL_MS, MAX_REQUESTS_PER_HOUR, generateResetCode, hashCode, judgeAttempt, normalizeCode, type Verdict } from './code';
import { composeResetEmail } from './email';

const normEmail = (e: string) => e.trim().toLowerCase();
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Emails a fresh code to the account owner. Always looks the same from outside (unknown email, rate-limited
 * or sent), so nobody can use this to find out who has an account.
 */
export async function requestReset(rawEmail: string, language?: string): Promise<void> {
  const email = normEmail(rawEmail);
  const [u] = await db.select({ id: user.id, name: user.name, email: user.email }).from(user).where(eq(user.email, email)).limit(1);
  if (!u) {
    await sleep(600); // roughly the time a real send takes
    return;
  }

  const since = new Date(Date.now() - 60 * 60 * 1000);
  const [{ n }] = await db
    .select({ n: count() })
    .from(passwordResets)
    .where(and(eq(passwordResets.userId, u.id), gt(passwordResets.createdAt, since)));
  if (n >= MAX_REQUESTS_PER_HOUR) {
    await sleep(600);
    return;
  }

  // Only the newest code is valid.
  await db.update(passwordResets).set({ usedAt: new Date() }).where(and(eq(passwordResets.userId, u.id), isNull(passwordResets.usedAt)));

  const code = generateResetCode();
  await db.insert(passwordResets).values({
    userId: u.id,
    email,
    codeHash: hashCode(code, email, config.auth.secret),
    expiresAt: new Date(Date.now() + CODE_TTL_MS),
  });

  const mail = composeResetEmail({ code, name: u.name, language });
  const result = await sendEmail({ to: u.email, ...mail, fromName: 'SpendSync' });
  if (!result.ok) logger.warn('password reset email not sent', { reason: result.reason, error: result.error });
}

export type ResetOutcome = Verdict;

/** Checks the code and, if right, sets the new password and signs the user out everywhere. */
export async function confirmReset(rawEmail: string, rawCode: string, newPassword: string): Promise<ResetOutcome> {
  const email = normEmail(rawEmail);
  const code = normalizeCode(rawCode);
  const [u] = await db.select({ id: user.id }).from(user).where(eq(user.email, email)).limit(1);
  if (!u) return 'none';

  const [record] = await db
    .select()
    .from(passwordResets)
    .where(and(eq(passwordResets.userId, u.id), isNull(passwordResets.usedAt)))
    .orderBy(desc(passwordResets.createdAt))
    .limit(1);

  const verdict = judgeAttempt(record ?? null, { code, email, secret: config.auth.secret, now: new Date() });
  if (verdict === 'wrong' && record) {
    await db.update(passwordResets).set({ attempts: record.attempts + 1 }).where(eq(passwordResets.id, record.id));
  }
  if (verdict !== 'ok' || !record) return verdict;

  const hashed = await hashPassword(newPassword);
  const updated = await db
    .update(account)
    .set({ password: hashed, updatedAt: new Date() })
    .where(and(eq(account.userId, u.id), eq(account.providerId, 'credential')))
    .returning({ id: account.id });
  if (updated.length === 0) return 'none';

  await db.update(passwordResets).set({ usedAt: new Date() }).where(eq(passwordResets.id, record.id));
  await db.delete(session).where(eq(session.userId, u.id)); // anyone who had the old password is signed out
  return 'ok';
}
