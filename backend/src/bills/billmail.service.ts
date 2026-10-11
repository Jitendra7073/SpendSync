import { and, asc, eq, inArray, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { billEmailCounts, billReads, bills, transactions, user } from '../db/schema/index';
import { liveBill, liveTx } from '../lib/live';
import { sendEmail, smtpFromEnv } from '../lib/email';
import { ApiError } from '../utils/errors';
import { logger } from '../utils/logger';
import { requireCloudinary } from './service';
import { emailFileUrl } from './cloudinary';
import { utcDay } from './rules';
import { composeBillsEmail, emailCooldown, maskEmail, packAttachments, type MailFile } from './billmail';

async function fetchFile(url: string): Promise<Buffer | null> {
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(8_000) });
    return r.ok ? Buffer.from(await r.arrayBuffer()) : null;
  } catch {
    return null;
  }
}

export async function sendBillsEmail(userId: string, ids: string[]) {
  const c = requireCloudinary();
  if (!smtpFromEnv()) throw new ApiError(503, 'Email is not set up on the server', 'EMAIL_NOT_CONFIGURED');
  const [u] = await db.select({ email: user.email }).from(user).where(eq(user.id, userId)).limit(1);
  if (!u?.email) throw new ApiError(400, 'Your account has no email address', 'NO_EMAIL');

  const day = utcDay(new Date());
  // Claim the slot BEFORE sending, in one statement: two requests at the same moment can't both pass.
  if (!(await reserveSlot(userId, day))) {
    const [count] = await db.select().from(billEmailCounts).where(and(eq(billEmailCounts.userId, userId), eq(billEmailCounts.day, day)));
    const gate = emailCooldown(count ? { count: count.count, lastAt: count.lastAt } : undefined, new Date());
    const wait = gate.ok ? 60 : gate.waitSec;
    throw new ApiError(429, gate.ok || gate.reason === 'minute' ? `Please wait ${wait} s before sending again` : 'Daily limit reached — try again tomorrow', 'EMAIL_COOLDOWN');
  }
  try {
    return await send(userId, ids, u.email, c);
  } catch (e) {
    await releaseSlot(userId, day); // nothing was sent: the user may try again right away
    throw e;
  }
}

/** Atomically: +1 for today unless the last send was < 60 s ago or 20 were sent today. False = refused. */
async function reserveSlot(userId: string, day: string): Promise<boolean> {
  const now = new Date();
  const cutoff = new Date(now.getTime() - 60_000);
  const rows = await db.execute(sql`
    INSERT INTO bill_email_counts (user_id, day, count, last_at)
    VALUES (${userId}, ${day}, 1, ${now.toISOString()}::timestamp)
    ON CONFLICT (user_id, day) DO UPDATE
      SET count = bill_email_counts.count + 1, last_at = ${now.toISOString()}::timestamp
      WHERE bill_email_counts.last_at < ${cutoff.toISOString()}::timestamp AND bill_email_counts.count < 20
    RETURNING count`);
  return (rows as unknown as unknown[]).length > 0;
}

/** Undo a reservation whose email never went out. */
async function releaseSlot(userId: string, day: string) {
  const earlier = new Date(Date.now() - 61_000);
  await db.execute(sql`
    UPDATE bill_email_counts SET count = GREATEST(count - 1, 0), last_at = ${earlier.toISOString()}::timestamp
    WHERE user_id = ${userId} AND day = ${day}`);
}

async function send(userId: string, ids: string[], email: string, c: ReturnType<typeof requireCloudinary>) {

  const txs = await db
    .select({ id: transactions.id, date: transactions.createdAt, merchant: transactions.merchant, amount: transactions.amount })
    .from(transactions)
    .where(and(eq(transactions.userId, userId), inArray(transactions.id, ids), liveTx))
    .orderBy(asc(transactions.createdAt));
  const files = await db
    .select({ txId: bills.transactionId, publicId: bills.publicId, version: bills.version, format: bills.format, position: bills.position })
    .from(bills)
    .where(and(eq(bills.userId, userId), inArray(bills.transactionId, ids), eq(bills.status, 'ready'), liveBill))
    .orderBy(asc(bills.position));
  const reads = await db.select({ txId: billReads.transactionId, data: billReads.data }).from(billReads)
    .where(and(eq(billReads.userId, userId), inArray(billReads.transactionId, ids), eq(billReads.status, 'done')));

  const withFiles = txs.filter((t) => files.some((f) => f.txId === t.id));
  const skipped = ids.length - withFiles.length;
  const ordered = withFiles.flatMap((t) => files.filter((f) => f.txId === t.id).map((f, i) => ({ t, f, i })));
  const mailFiles: MailFile[] = await Promise.all(
    ordered.map(async ({ t, f, i }) => {
      const u2 = emailFileUrl(c, f);
      const label = `${t.date.toISOString().slice(0, 10)} ${t.merchant} — page ${i + 1}`;
      return { filename: `${t.date.toISOString().slice(0, 10)}-${t.merchant.replace(/[^\w-]+/g, '_').slice(0, 30)}-${i + 1}.${u2.filename.split('.').pop()}`, contentType: u2.contentType, bytes: await fetchFile(u2.url), label };
    }),
  );
  const { attached, notAttached } = packAttachments(mailFiles);
  if (attached.length === 0) throw new ApiError(503, "Bills couldn't be fetched, please try again", 'BILLS_UNAVAILABLE');

  const summaryOf = (txId: string) => {
    const d = reads.find((r) => r.txId === txId)?.data as { summary?: unknown } | undefined;
    return typeof d?.summary === 'string' ? d.summary.slice(0, 300) : null;
  };
  const mail = composeBillsEmail(
    withFiles.map((t) => ({ date: t.date.toISOString().slice(0, 10), merchant: t.merchant, amount: Number(t.amount), summary: summaryOf(t.id), files: files.filter((f) => f.txId === t.id).length })),
    notAttached,
    skipped,
  );
  const sent = await sendEmail({ to: email, ...mail, attachments: attached.map((f) => ({ filename: f.filename, content: f.bytes!, contentType: f.contentType })) });
  if (!sent.ok) {
    if (sent.reason === 'not_configured') throw new ApiError(503, 'Email is not set up on the server', 'EMAIL_NOT_CONFIGURED');
    throw new ApiError(502, "The email couldn't be sent, please try again", 'EMAIL_FAILED');
  }
  const sentTo = maskEmail(email);
  logger.info('Bills emailed', { sentTo, attached: attached.length, notAttached: notAttached.length, skipped });
  return { sentTo, attached: attached.length, notAttached: notAttached.length, skipped };
}
