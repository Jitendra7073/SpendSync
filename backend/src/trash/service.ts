import { and, desc, eq, inArray, isNotNull, isNull, sql, type SQL } from 'drizzle-orm';
import { db } from '../db/index';
import { bills, holds, transactions, type Hold, type Transaction } from '../db/schema/index';
import { config } from '../config/env';
import { toView, type BillView } from '../bills/service';
import { enqueuePurges, runPurgesInline } from '../bills/purge';
import type { TrashKind } from '../types/trash.types';
import { assertInTrash, decodeCursor, mergePage, type TrashCursor } from './rules';

export type TrashItem =
  | { kind: 'transaction'; id: string; deletedAt: Date; transaction: Transaction; holds: Hold[]; bills: BillView[] }
  | { kind: 'hold'; id: string; deletedAt: Date; hold: Hold }
  | { kind: 'bill'; id: string; deletedAt: Date; bill: BillView; merchant: string };

/** (deleted_at, id) strictly older than the cursor — same order as the ORDER BY below. */
function olderThan(
  c: TrashCursor | null,
  deletedAt: typeof transactions.deletedAt | typeof holds.deletedAt | typeof bills.deletedAt,
  id: typeof transactions.id | typeof holds.id | typeof bills.id,
): SQL | undefined {
  return c ? sql`(${deletedAt}, ${id}) < (${c.at}::timestamp, ${c.id}::uuid)` : undefined;
}

export const trashService = {
  /** Deleted transactions (with their deleted holds nested) + holds deleted on their own, newest first. */
  async list(userId: string, cursor: string | undefined, limit: number) {
    const c = decodeCursor(cursor);

    const txRows = await db
      .select()
      .from(transactions)
      .where(and(eq(transactions.userId, userId), isNotNull(transactions.deletedAt), olderThan(c, transactions.deletedAt, transactions.id)))
      .orderBy(desc(transactions.deletedAt), desc(transactions.id))
      .limit(limit + 1);

    const holdRows = await db
      .select({ hold: holds })
      .from(holds)
      .innerJoin(transactions, eq(transactions.id, holds.transactionId))
      .where(and(eq(holds.userId, userId), isNotNull(holds.deletedAt), isNull(transactions.deletedAt), olderThan(c, holds.deletedAt, holds.id)))
      .orderBy(desc(holds.deletedAt), desc(holds.id))
      .limit(limit + 1);

    // Without Cloudinary configured there are no bill files to show.
    const billRows = config.bills.cloudinary
      ? await db
          .select({ bill: bills, merchant: transactions.merchant })
          .from(bills)
          .innerJoin(transactions, eq(transactions.id, bills.transactionId))
          .where(and(eq(bills.userId, userId), eq(bills.status, 'ready'), isNotNull(bills.deletedAt), isNull(transactions.deletedAt), olderThan(c, bills.deletedAt, bills.id)))
          .orderBy(desc(bills.deletedAt), desc(bills.id))
          .limit(limit + 1)
      : [];

    const page = mergePage<TrashItem>(
      [
        ...txRows.map((t) => ({ kind: 'transaction' as const, id: t.id, deletedAt: t.deletedAt!, transaction: t, holds: [], bills: [] })),
        ...holdRows.map((r) => ({ kind: 'hold' as const, id: r.hold.id, deletedAt: r.hold.deletedAt!, hold: r.hold })),
      ],
      billRows.map((r) => ({ kind: 'bill' as const, id: r.bill.id, deletedAt: r.bill.deletedAt!, bill: toView(r.bill), merchant: r.merchant })),
      limit,
    );

    const txIds = page.items.filter((i) => i.kind === 'transaction').map((i) => i.id);
    if (txIds.length) {
      const nested = await db
        .select()
        .from(holds)
        .where(and(eq(holds.userId, userId), inArray(holds.transactionId, txIds), isNotNull(holds.deletedAt)));
      const nestedBills = config.bills.cloudinary
        ? await db.select().from(bills).where(and(eq(bills.userId, userId), inArray(bills.transactionId, txIds), eq(bills.status, 'ready'), isNotNull(bills.deletedAt)))
        : [];
      for (const item of page.items) {
        if (item.kind === 'transaction') {
          item.holds = nested.filter((h) => h.transactionId === item.id);
          item.bills = nestedBills.filter((b) => b.transactionId === item.id).map(toView);
        }
      }
    }
    return page;
  },

  /** Irreversible. Only for rows already in the Trash (see assertInTrash). Bill files leave Cloudinary via the purge outbox. */
  async deleteForever(userId: string, kind: TrashKind, id: string) {
    let purgeIds: string[] = [];
    if (kind === 'transaction') {
      const mine = and(eq(transactions.id, id), eq(transactions.userId, userId));
      const [row] = await db.select({ deletedAt: transactions.deletedAt }).from(transactions).where(mine);
      assertInTrash(row);
      purgeIds = await db.transaction(async (tx) => {
        // trash-aware: every file of this transaction goes, whatever its state
        const files = await tx.select({ publicId: bills.publicId }).from(bills).where(and(eq(bills.transactionId, id), eq(bills.userId, userId)));
        const ids = await enqueuePurges(tx, files.map((f) => f.publicId));
        await tx.delete(transactions).where(and(mine, isNotNull(transactions.deletedAt))); // FK cascade removes holds + bills
        return ids;
      });
    } else if (kind === 'hold') {
      const mine = and(eq(holds.id, id), eq(holds.userId, userId));
      const [row] = await db.select({ deletedAt: holds.deletedAt }).from(holds).where(mine);
      assertInTrash(row);
      await db.delete(holds).where(and(mine, isNotNull(holds.deletedAt)));
    } else {
      const mine = and(eq(bills.id, id), eq(bills.userId, userId));
      const [row] = await db.select({ deletedAt: bills.deletedAt, publicId: bills.publicId }).from(bills).where(mine);
      assertInTrash(row);
      purgeIds = await db.transaction(async (tx) => {
        const ids = await enqueuePurges(tx, [row!.publicId]);
        await tx.delete(bills).where(and(mine, isNotNull(bills.deletedAt)));
        return ids;
      });
    }
    await runPurgesInline(purgeIds);
  },

  async empty(userId: string) {
    const { deleted, purgeIds } = await db.transaction(async (tx) => {
      // trash-aware: deleted bills, and every bill of a deleted transaction
      const files = await tx
        .select({ publicId: bills.publicId })
        .from(bills)
        .leftJoin(transactions, eq(transactions.id, bills.transactionId))
        .where(and(eq(bills.userId, userId), sql`(${bills.deletedAt} IS NOT NULL OR ${transactions.deletedAt} IS NOT NULL)`));
      const purgeIds = await enqueuePurges(tx, files.map((f) => f.publicId));
      const b = await tx.delete(bills).where(and(eq(bills.userId, userId), isNotNull(bills.deletedAt))).returning({ id: bills.id });
      const h = await tx.delete(holds).where(and(eq(holds.userId, userId), isNotNull(holds.deletedAt))).returning({ id: holds.id });
      const t = await tx.delete(transactions).where(and(eq(transactions.userId, userId), isNotNull(transactions.deletedAt))).returning({ id: transactions.id });
      return { deleted: b.length + h.length + t.length, purgeIds };
    });
    await runPurgesInline(purgeIds);
    return { deleted };
  },
};
