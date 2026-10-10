import { and, desc, eq, inArray, isNotNull, isNull, sql, type SQL } from 'drizzle-orm';
import { db } from '../db/index';
import { holds, transactions, type Hold, type Transaction } from '../db/schema/index';
import type { TrashKind } from '../types/trash.types';
import { assertInTrash, decodeCursor, mergePage, type TrashCursor } from './rules';

export type TrashItem =
  | { kind: 'transaction'; id: string; deletedAt: Date; transaction: Transaction; holds: Hold[] }
  | { kind: 'hold'; id: string; deletedAt: Date; hold: Hold };

/** (deleted_at, id) strictly older than the cursor — same order as the ORDER BY below. */
function olderThan(c: TrashCursor | null, deletedAt: typeof transactions.deletedAt | typeof holds.deletedAt, id: typeof transactions.id | typeof holds.id): SQL | undefined {
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

    const page = mergePage<TrashItem>(
      txRows.map((t) => ({ kind: 'transaction', id: t.id, deletedAt: t.deletedAt!, transaction: t, holds: [] })),
      holdRows.map((r) => ({ kind: 'hold', id: r.hold.id, deletedAt: r.hold.deletedAt!, hold: r.hold })),
      limit,
    );

    const txIds = page.items.filter((i) => i.kind === 'transaction').map((i) => i.id);
    if (txIds.length) {
      const nested = await db
        .select()
        .from(holds)
        .where(and(eq(holds.userId, userId), inArray(holds.transactionId, txIds), isNotNull(holds.deletedAt)));
      for (const item of page.items) {
        if (item.kind === 'transaction') item.holds = nested.filter((h) => h.transactionId === item.id);
      }
    }
    return page;
  },

  /** Irreversible. Only for rows already in the Trash (see assertInTrash). */
  async deleteForever(userId: string, kind: TrashKind, id: string) {
    if (kind === 'transaction') {
      const mine = and(eq(transactions.id, id), eq(transactions.userId, userId));
      const [row] = await db.select({ deletedAt: transactions.deletedAt }).from(transactions).where(mine);
      assertInTrash(row);
      // FK cascade removes its holds.
      await db.delete(transactions).where(and(mine, isNotNull(transactions.deletedAt)));
    } else {
      const mine = and(eq(holds.id, id), eq(holds.userId, userId));
      const [row] = await db.select({ deletedAt: holds.deletedAt }).from(holds).where(mine);
      assertInTrash(row);
      await db.delete(holds).where(and(mine, isNotNull(holds.deletedAt)));
    }
  },

  async empty(userId: string) {
    return db.transaction(async (tx) => {
      const h = await tx.delete(holds).where(and(eq(holds.userId, userId), isNotNull(holds.deletedAt))).returning({ id: holds.id });
      const t = await tx.delete(transactions).where(and(eq(transactions.userId, userId), isNotNull(transactions.deletedAt))).returning({ id: transactions.id });
      return { deleted: h.length + t.length };
    });
  },
};
