import { and, eq, gte, lt } from 'drizzle-orm';
import { liveHold, liveTx } from '../lib/live';
import { z } from 'zod';
import { db } from '../db/index';
import { holds, transactions, user } from '../db/schema/index';
import { sendEmail } from '../lib/email';
import { loadPlan } from '../planify/service';
import { BadRequestError } from '../utils/errors';
import { buildAttachments, type ExportInput } from './build';

const day = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const exportEmailSchema = z.object({
  from: day.optional(),
  to: day.optional(),
  kinds: z.array(z.enum(['transactions', 'holds', 'plan'])).min(1).max(3),
  type: z.enum(['all', 'income', 'expense']).default('all'),
  format: z.enum(['csv', 'json']).default('csv'),
});
export type ExportEmailInput = z.infer<typeof exportEmailSchema>;

/** One export email per user every 5 minutes, so it cannot be used to flood an inbox. */
const lastSent = new Map<string, number>();
const COOLDOWN_MS = 5 * 60_000;

/** Months (YYYY-MM) touched by [from, to]; at most 12 so a plan export stays small. */
export function monthsBetween(from: string, to: string): string[] {
  const out: string[] = [];
  let [y, m] = from.slice(0, 7).split('-').map(Number);
  const [ty, tm] = to.slice(0, 7).split('-').map(Number);
  while ((y < ty || (y === ty && m <= tm)) && out.length < 12) {
    out.push(`${y}-${String(m).padStart(2, '0')}`);
    m += 1;
    if (m > 12) {
      m = 1;
      y += 1;
    }
  }
  return out;
}

/** Emails the data of the signed-in user to THEIR account address only. Returns what was sent. */
export async function emailExport(userId: string, input: ExportEmailInput, now = Date.now()) {
  const [u] = await db.select({ email: user.email, name: user.name }).from(user).where(eq(user.id, userId)).limit(1);
  if (!u?.email) throw new BadRequestError('Your account has no email address');
  const last = lastSent.get(userId) ?? 0;
  if (now - last < COOLDOWN_MS) throw new BadRequestError('An export was just sent. Please wait a few minutes.');

  const from = input.from ? new Date(`${input.from}T00:00:00.000Z`) : new Date(0);
  const to = input.to ? new Date(Date.parse(`${input.to}T00:00:00.000Z`) + 86_400_000) : new Date('9999-12-31T00:00:00.000Z');
  const data: ExportInput = {};

  if (input.kinds.includes('transactions')) {
    const rows = await db
      .select()
      .from(transactions)
      .where(and(eq(transactions.userId, userId), liveTx, gte(transactions.createdAt, from), lt(transactions.createdAt, to)))
      .orderBy(transactions.createdAt);
    data.transactions = rows.filter((r) => input.type === 'all' || (input.type === 'income' ? r.type === 'credit' : r.type === 'debit'));
  }
  if (input.kinds.includes('holds')) {
    data.holds = await db.select().from(holds).where(and(eq(holds.userId, userId), liveHold)).orderBy(holds.createdAt);
  }
  if (input.kinds.includes('plan')) {
    const today = new Date(now).toISOString().slice(0, 10);
    const months = monthsBetween(input.from ?? new Date(now - 90 * 86_400_000).toISOString().slice(0, 10), input.to ?? today);
    data.plan = [];
    for (const month of months) {
      const view = await loadPlan(userId, month, today);
      if (view.exists) for (const b of view.status.buckets) data.plan.push({ month, bucket: b.name, kind: b.kind, limit: b.limit, spent: b.spent });
    }
  }

  const attachments = buildAttachments(data, input.format);
  const counts = [
    data.transactions ? `${data.transactions.length} transactions` : '',
    data.holds ? `${data.holds.length} holds` : '',
    data.plan ? `${data.plan.length} plan lines` : '',
  ].filter(Boolean).join(', ');
  const result = await sendEmail({
    to: u.email,
    subject: 'Your SpendSync data export',
    text: `Hi ${u.name || 'there'},\n\nHere is the export you asked for (${counts}), attached as ${input.format.toUpperCase()}.\n\nThis was sent only to your account address. If you did not ask for it, you can ignore this email.\n\n- SpendSync`,
    fromName: 'SpendSync',
    attachments,
  });
  if (!result.ok) throw new BadRequestError(result.reason === 'not_configured' ? 'Email is not set up on the server' : 'The email could not be sent');
  lastSent.set(userId, now);
  return { sentTo: u.email.replace(/^(.).*(@.*)$/, '$1***$2'), files: attachments.map((a) => a.filename), counts };
}
