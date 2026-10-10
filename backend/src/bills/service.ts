import { randomUUID } from 'node:crypto';
import { and, asc, eq, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { billUploadCounts, bills, transactions, type Bill } from '../db/schema/index';
import { config } from '../config/env';
import { liveBill } from '../lib/live';
import { transactionService } from '../services/transaction.service';
import { assertParentLive } from '../trash/rules';
import type { ReserveBillInput, UploadResult } from '../types/bill.types';
import { ApiError, BadRequestError, NotFoundError } from '../utils/errors';
import { deliveryUrls, uploadParams, verifyUploadResponse, type BillUrls, type CloudinaryCreds } from './cloudinary';
import { enqueuePurges, runPurges } from './purge';
import { checkDaily, checkQuota, confirmDecision, firstFreePosition, newPublicId, restorePosition, utcDay } from './rules';

export type BillView = Pick<Bill, 'id' | 'transactionId' | 'position' | 'status' | 'format' | 'pages' | 'bytes'> & BillUrls;
export type Reservation = { billId: string; status: 'pending' | 'ready'; uploadUrl?: string; params?: Record<string, string> };

export function requireCloudinary(): CloudinaryCreds {
  const c = config.bills.cloudinary;
  if (!c) throw new ApiError(503, 'Bills are not configured', 'BILLS_NOT_CONFIGURED');
  return c;
}

export function toView(row: Bill): BillView {
  const c = requireCloudinary();
  return {
    id: row.id, transactionId: row.transactionId, position: row.position, status: row.status,
    format: row.format, pages: row.pages, bytes: row.bytes,
    ...deliveryUrls(c, { publicId: row.publicId, version: row.version, format: row.format, pages: row.pages }),
  };
}

function reservation(c: CloudinaryCreds, row: Bill): Reservation {
  if (row.status === 'ready') return { billId: row.id, status: 'ready' };
  const { uploadUrl, params } = uploadParams(c, row.publicId, config.bills.notificationUrl, Math.floor(Date.now() / 1000));
  return { billId: row.id, status: 'pending', uploadUrl, params };
}

export const billService = {
  async reserve(userId: string, txId: string, input: ReserveBillInput): Promise<Reservation> {
    const c = requireCloudinary();
    // trash-aware: a retried reserve must find its row even if it was deleted meanwhile
    const [existing] = await db.select().from(bills).where(and(eq(bills.userId, userId), eq(bills.clientKey, input.clientKey)));
    if (existing) {
      if (existing.deletedAt) throw new ApiError(409, 'This bill was deleted', 'BILL_DELETED');
      return reservation(c, existing);
    }
    await transactionService.getById(userId, txId); // 404 unless the user's own, live transaction

    const row = await db.transaction(async (tx) => {
      // Serialises reserves on one transaction so two at once can't make a 6th page.
      await tx.execute(sql`SELECT id FROM transactions WHERE id = ${txId} FOR UPDATE`);
      const live = await tx.select({ id: bills.id, position: bills.position }).from(bills).where(and(eq(bills.transactionId, txId), eq(bills.userId, userId), liveBill));
      const replacing = input.replaces ? live.find((b) => b.id === input.replaces) : undefined;
      if (input.replaces && !replacing) throw new NotFoundError('Bill to replace not found');

      // trash-aware: bills in the Trash still use Cloudinary space
      const [{ used }] = await tx.select({ used: sql<string>`COALESCE(SUM(${bills.bytes}), 0)` }).from(bills).where(eq(bills.userId, userId));
      checkQuota({ livePages: live.length, replacing: Boolean(replacing), bytesUsed: Number(used), bytes: input.bytes, maxBytes: config.bills.maxBytes });

      const [{ count }] = await tx
        .insert(billUploadCounts)
        .values({ userId, day: utcDay(new Date()), count: 1 })
        .onConflictDoUpdate({ target: [billUploadCounts.userId, billUploadCounts.day], set: { count: sql`${billUploadCounts.count} + 1` } })
        .returning({ count: billUploadCounts.count });
      checkDaily(count, config.bills.maxUploadsPerDay);

      const taken = live.map((b) => b.position);
      const position =
        replacing?.position ??
        (input.position !== undefined && !taken.includes(input.position) ? input.position : firstFreePosition(taken));
      if (position === null) throw new ApiError(409, 'A transaction can have up to 5 bill pages', 'BILL_LIMIT');

      const [created] = await tx
        .insert(bills)
        .values({ userId, transactionId: txId, publicId: newPublicId(userId, randomUUID()), position, status: 'pending', clientKey: input.clientKey, replacesId: replacing?.id ?? null, bytes: input.bytes })
        .returning();
      return created;
    });
    return reservation(c, row);
  },

  async sign(userId: string, billId: string): Promise<Reservation> {
    const c = requireCloudinary();
    const [row] = await db.select().from(bills).where(and(eq(bills.id, billId), eq(bills.userId, userId), liveBill));
    if (!row) throw new NotFoundError('Bill not found');
    return reservation(c, row);
  },

  /** Phone (verified=false: checks Cloudinary's response signature) or webhook (verified=true by header). */
  async confirm(lookup: { userId: string; billId: string } | { publicId: string }, resp: UploadResult & { signature?: string }, verified: boolean): Promise<BillView | null> {
    const c = requireCloudinary();
    if (!verified && !(resp.signature && verifyUploadResponse(c, { public_id: resp.public_id, version: resp.version, signature: resp.signature }))) {
      throw new BadRequestError('Upload signature does not match', 'BAD_SIGNATURE');
    }
    let purgeIds: string[] = [];
    const result = await db.transaction(async (tx) => {
      const where = 'publicId' in lookup ? eq(bills.publicId, lookup.publicId) : and(eq(bills.id, lookup.billId), eq(bills.userId, lookup.userId));
      // trash-aware: an upload finishing after its transaction went to the Trash still completes (it stays in the Trash)
      const [row] = await tx.select().from(bills).where(where).for('update');
      const decision = confirmDecision(row, resp.public_id, resp.bytes);
      if (decision === 'unknown') return null;
      if (decision === 'mismatch') throw new BadRequestError('Upload does not belong to this bill', 'PUBLIC_ID_MISMATCH');
      if (decision === 'replay') return row;
      if (decision === 'too_large') {
        purgeIds = await enqueuePurges(tx, [row.publicId]);
        await tx.delete(bills).where(eq(bills.id, row.id));
        return 'too_large' as const;
      }
      const [ready] = await tx
        .update(bills)
        .set({ status: 'ready', format: resp.format, bytes: resp.bytes, width: resp.width ?? null, height: resp.height ?? null, pages: resp.pages ?? null, version: resp.version, updatedAt: new Date() })
        .where(eq(bills.id, row.id))
        .returning();
      if (row.replacesId) await tx.update(bills).set({ deletedAt: new Date() }).where(and(eq(bills.id, row.replacesId), liveBill));
      return ready;
    });
    if (result === 'too_large') {
      await runPurges({ ids: purgeIds });
      throw new ApiError(422, 'This file is larger than 10 MB', 'TOO_LARGE');
    }
    return result ? toView(result) : null;
  },

  async list(userId: string, txId: string): Promise<BillView[]> {
    await transactionService.getById(userId, txId);
    const rows = await db.select().from(bills).where(and(eq(bills.transactionId, txId), eq(bills.userId, userId), eq(bills.status, 'ready'), liveBill)).orderBy(asc(bills.position));
    return rows.map(toView);
  },

  async softDelete(userId: string, id: string) {
    const mine = and(eq(bills.id, id), eq(bills.userId, userId));
    const [row] = await db.select({ deletedAt: bills.deletedAt }).from(bills).where(mine);
    if (!row) throw new NotFoundError('Bill not found');
    if (row.deletedAt) return;
    await db.update(bills).set({ deletedAt: new Date() }).where(and(mine, liveBill));
  },

  async restore(userId: string, id: string): Promise<BillView> {
    const restored = await db.transaction(async (tx) => {
      const mine = and(eq(bills.id, id), eq(bills.userId, userId));
      const [row] = await tx.select({ bill: bills, parentDeletedAt: transactions.deletedAt }).from(bills).innerJoin(transactions, eq(transactions.id, bills.transactionId)).where(mine);
      if (!row) throw new NotFoundError('Bill not found');
      if (!row.bill.deletedAt) return row.bill;
      assertParentLive(row.parentDeletedAt);
      await tx.execute(sql`SELECT id FROM transactions WHERE id = ${row.bill.transactionId} FOR UPDATE`);
      const live = await tx.select({ position: bills.position }).from(bills).where(and(eq(bills.transactionId, row.bill.transactionId), eq(bills.userId, userId), liveBill));
      const position = restorePosition(live.map((b) => b.position), row.bill.position);
      const [bill] = await tx.update(bills).set({ deletedAt: null, position, updatedAt: new Date() }).where(mine).returning();
      return bill;
    });
    return toView(restored);
  },

  async usage(userId: string) {
    // trash-aware: Trash still counts toward storage
    const [{ used }] = await db.select({ used: sql<string>`COALESCE(SUM(${bills.bytes}), 0)` }).from(bills).where(eq(bills.userId, userId));
    const [today] = await db.select({ count: billUploadCounts.count }).from(billUploadCounts).where(and(eq(billUploadCounts.userId, userId), eq(billUploadCounts.day, utcDay(new Date()))));
    return { bytesUsed: Number(used), bytesLimit: config.bills.maxBytes, uploadsToday: today?.count ?? 0, uploadsPerDay: config.bills.maxUploadsPerDay };
  },
};
