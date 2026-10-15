import { and, desc, eq, gte, lte, lt, ne, sql, count } from 'drizzle-orm';
import { db } from '../db/index';
import { capturedMessages, transactions, userSettings, type CapturedMessage } from '../db/schema/index';
import { categoryService } from '../services/category.service';
import { ConflictError, NotFoundError, ValidationError } from '../utils/errors';
import type { CaptureAction, IngestInput, ListQuery } from '../types/capture.types';
import { decide, maskBody, OTP, windowMs, type Candidate, type Source } from './decide';

type Tx = Parameters<Parameters<typeof db.transaction>[0]>[0];
const DAY = 24 * 60 * 60 * 1000;

async function prefsOf(tx: Tx, userId: string) {
  const [s] = await tx.select().from(userSettings).where(eq(userSettings.userId, userId));
  return {
    mode: (s?.captureMode === 'ask' ? 'ask' : 'auto') as 'auto' | 'ask',
    duplicates: (s?.captureDuplicates === 'keep' ? 'keep' : 'ask') as 'ask' | 'keep',
    keep: s?.captureKeepMessages ?? true,
    retentionDays: s?.captureRetentionDays ?? 365,
  };
}

/**
 * The transaction takes the message's id, so the phone can edit a payment it captured offline (it only
 * knows that id) and the edit lands on the right row after sync. A deleted one may be re-added under it.
 */
async function newTransaction(tx: Tx, userId: string, m: Pick<CapturedMessage, 'id' | 'amount' | 'type' | 'payee' | 'sender' | 'packageName' | 'receivedAt'>, category: string) {
  if (!m.amount || (m.type !== 'debit' && m.type !== 'credit')) throw new ValidationError('This message has no amount to add');
  const [row] = await tx
    .insert(transactions)
    .values({
      id: m.id,
      userId,
      amount: m.amount,
      type: m.type,
      merchant: m.payee || 'Unknown',
      category,
      sourceApp: m.packageName ?? m.sender,
      createdAt: m.receivedAt,
    })
    .returning();
  return row;
}

/** A duplicate that knows the payee fills in a transaction still called "Unknown". */
async function fillMerchant(tx: Tx, userId: string, transactionId: string | null, payee: string | null | undefined) {
  if (!transactionId || !payee) return;
  await tx
    .update(transactions)
    .set({ merchant: payee, updatedAt: new Date() })
    .where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId), eq(transactions.merchant, 'Unknown')));
}

async function withTransaction(message: CapturedMessage) {
  if (!message.transactionId) return { message, transaction: null };
  const [transaction] = await db.select().from(transactions).where(eq(transactions.id, message.transactionId));
  return { message, transaction: transaction ?? null };
}

/**
 * Stores one message and decides add / duplicate / review / ignore, atomically per user: the lock makes
 * an SMS and an email for the same payment, arriving at the same moment from two devices, queue up
 * instead of both creating a transaction. Retrying the same id returns the first result.
 */
export async function ingest(userId: string, input: IngestInput) {
  if (OTP.test(input.body)) throw new ValidationError('One-time codes are never stored', 'OTP_NOT_STORED');
  // Suggest outside the lock: it only reads the user's keyword list.
  const category = input.payee ? ((await categoryService.suggestCategory(userId, input.payee)) ?? 'Other') : 'Other';

  const message = await db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${userId}))`);

    const [existing] = await tx.select().from(capturedMessages).where(eq(capturedMessages.id, input.id));
    if (existing) {
      if (existing.userId !== userId) throw new ConflictError('Message id already in use');
      return existing;
    }

    const prefs = await prefsOf(tx, userId);
    const receivedAt = new Date(input.receivedAt);
    const base = {
      id: input.id,
      userId,
      source: input.source,
      sender: input.sender,
      packageName: input.packageName ?? null,
      body: prefs.keep ? maskBody(input.body) : '',
      receivedAt,
      amount: input.amount != null ? input.amount.toFixed(2) : null,
      type: input.type ?? null,
      payee: input.payee || null,
      refNo: input.refNo || null,
      last4: input.last4 ?? null,
      balance: input.balance != null ? input.balance.toFixed(2) : null,
    };

    if (prefs.retentionDays > 0) {
      await tx
        .delete(capturedMessages)
        .where(and(eq(capturedMessages.userId, userId), lt(capturedMessages.receivedAt, new Date(Date.now() - prefs.retentionDays * DAY))));
    }

    if (input.ignoreReason) {
      // Without copies there is nothing to show for an ignored message, so it is not kept at all.
      if (!prefs.keep) return { ...base, status: 'ignored', reason: input.ignoreReason, transactionId: null, duplicateOf: null, createdAt: new Date() } as CapturedMessage;
      const [row] = await tx.insert(capturedMessages).values({ ...base, status: 'ignored', reason: input.ignoreReason }).returning();
      return row;
    }

    const amount = input.amount!;
    const type = input.type!;
    const refMatch = input.refNo
      ? (
          await tx
            .select({ messageId: capturedMessages.id, transactionId: capturedMessages.transactionId })
            .from(capturedMessages)
            .where(
              and(
                eq(capturedMessages.userId, userId),
                eq(capturedMessages.refNo, input.refNo),
                ne(capturedMessages.status, 'ignored'),
                gte(capturedMessages.receivedAt, new Date(receivedAt.getTime() - 7 * DAY)),
              ),
            )
            .limit(1)
        )[0] ?? null
      : null;

    const widest = windowMs('email', null);
    const rows = await tx
      .select({
        transactionId: transactions.id,
        at: transactions.createdAt,
        messageId: capturedMessages.id,
        source: capturedMessages.source,
        refNo: capturedMessages.refNo,
        last4: capturedMessages.last4,
        payee: capturedMessages.payee,
      })
      .from(transactions)
      .leftJoin(capturedMessages, and(eq(capturedMessages.transactionId, transactions.id), eq(capturedMessages.status, 'added')))
      .where(
        and(
          eq(transactions.userId, userId),
          eq(transactions.type, type),
          eq(transactions.amount, amount.toFixed(2)),
          gte(transactions.createdAt, new Date(receivedAt.getTime() - widest)),
          lte(transactions.createdAt, new Date(receivedAt.getTime() + widest)),
        ),
      )
      .limit(20);
    const candidates: Candidate[] = rows.map((r) => ({ ...r, source: r.source as Source | null }));

    const decision = decide({ source: input.source, amount, type, at: receivedAt, refNo: input.refNo, last4: input.last4, payee: input.payee }, refMatch, candidates, prefs);

    if (decision.status === 'added') {
      const created = await newTransaction(tx, userId, { ...base }, category);
      const [row] = await tx.insert(capturedMessages).values({ ...base, status: 'added', transactionId: created.id }).returning();
      return row;
    }
    if (decision.status === 'duplicate') await fillMerchant(tx, userId, decision.transactionId, input.payee);
    const [row] = await tx
      .insert(capturedMessages)
      .values({
        ...base,
        status: decision.status,
        reason: decision.status === 'review' ? decision.reason : null,
        transactionId: decision.transactionId,
        duplicateOf: decision.duplicateOf,
      })
      .returning();
    return row;
  });
  return withTransaction(message);
}

export async function list(userId: string, q: ListQuery) {
  const where = and(
    eq(capturedMessages.userId, userId),
    q.status ? eq(capturedMessages.status, q.status) : undefined,
    q.transactionId ? eq(capturedMessages.transactionId, q.transactionId) : undefined,
  );
  const [rows, [{ total }]] = await Promise.all([
    db.select().from(capturedMessages).where(where).orderBy(desc(capturedMessages.receivedAt)).limit(q.limit).offset((q.page - 1) * q.limit),
    db.select({ total: count() }).from(capturedMessages).where(where),
  ]);
  return { rows, meta: { page: q.page, limit: q.limit, total, totalPages: Math.ceil(total / q.limit) } };
}

/** The user's answer from the history screen or a review notification. */
export async function act(userId: string, id: string, action: CaptureAction) {
  const message = await db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${userId}))`);
    const [m] = await tx.select().from(capturedMessages).where(and(eq(capturedMessages.id, id), eq(capturedMessages.userId, userId)));
    if (!m) throw new NotFoundError('Message not found');

    const set = async (patch: Partial<CapturedMessage>) =>
      (await tx.update(capturedMessages).set(patch).where(eq(capturedMessages.id, id)).returning())[0];

    switch (action) {
      case 'same': // "yes, I already have this one"
        if (m.status !== 'review' || !m.transactionId) throw new ValidationError('Nothing to compare this message with');
        await fillMerchant(tx, userId, m.transactionId, m.payee);
        return set({ status: 'duplicate', reason: null });
      case 'add':
      case 'different': {
        // An added message already has its own transaction; a second tap must not make another.
        if (m.status === 'added' && m.transactionId) return m;
        const category = m.payee ? ((await categoryService.suggestCategory(userId, m.payee)) ?? 'Other') : 'Other';
        const created = await newTransaction(tx, userId, m, category);
        return set({ status: 'added', reason: null, transactionId: created.id, duplicateOf: null });
      }
      case 'ignore':
        return set({ status: 'ignored', reason: 'dismissed', transactionId: m.status === 'added' ? m.transactionId : null });
    }
  });
  return withTransaction(message);
}

export async function remove(userId: string, id: string) {
  const deleted = await db.delete(capturedMessages).where(and(eq(capturedMessages.id, id), eq(capturedMessages.userId, userId))).returning({ id: capturedMessages.id });
  if (deleted.length === 0) throw new NotFoundError('Message not found');
}

export async function removeAll(userId: string) {
  const deleted = await db.delete(capturedMessages).where(eq(capturedMessages.userId, userId)).returning({ id: capturedMessages.id });
  return { deleted: deleted.length };
}
