import { eq, and, desc, gte, lte, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { holds, transactions } from '../db/schema/index';
import { liveHold, liveTx } from '../lib/live';
import type { CreateTransactionInput, UpdateTransactionInput, TransactionQuery } from '../types/transaction.types';
import { NotFoundError } from '../utils/errors';

/**
 * Transaction Service
 * Handles all transaction-related business logic
 */
export class TransactionService {
  /**
   * Create a new transaction
   */
  async create(userId: string, data: CreateTransactionInput) {
    const [transaction] = await db
      .insert(transactions)
      .values({
        userId,
        amount: data.amount,
        type: data.type,
        merchant: data.merchant,
        category: data.category,
        sourceApp: data.sourceApp,
        note: data.note,
        ...(data.transactionDate ? { createdAt: new Date(data.transactionDate) } : {}),
      })
      .returning();

    return transaction;
  }

  /**
   * Get transactions for a user with filters
   */
  async getAll(userId: string, query: TransactionQuery) {
    const { startDate, endDate, category, type, page, limit } = query;
    const offset = (page - 1) * limit;

    // Build where conditions
    const conditions = [eq(transactions.userId, userId), liveTx];

    if (startDate) {
      conditions.push(gte(transactions.createdAt, new Date(startDate)));
    }

    if (endDate) {
      conditions.push(lte(transactions.createdAt, new Date(endDate)));
    }

    if (category) {
      conditions.push(eq(transactions.category, category));
    }

    if (type) {
      conditions.push(eq(transactions.type, type));
    }

    // Get transactions
    const results = await db
      .select()
      .from(transactions)
      .where(and(...conditions))
      .orderBy(desc(transactions.createdAt))
      .limit(limit)
      .offset(offset);

    // Get total count
    const [{ count }] = await db
      .select({ count: sql<number>`count(*)` })
      .from(transactions)
      .where(and(...conditions));

    return {
      transactions: results,
      meta: {
        page,
        limit,
        total: Number(count),
        totalPages: Math.ceil(Number(count) / limit),
      },
    };
  }

  /**
   * Get a single transaction by ID
   */
  async getById(userId: string, transactionId: string) {
    const [transaction] = await db
      .select()
      .from(transactions)
      .where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId), liveTx));

    if (!transaction) {
      throw new NotFoundError('Transaction not found');
    }

    return transaction;
  }

  /**
   * Update a transaction
   */
  async update(userId: string, transactionId: string, data: UpdateTransactionInput) {
    // Check if transaction exists and belongs to user
    await this.getById(userId, transactionId);

    // `transactionDate` isn't a real column — it maps onto `createdAt`.
    const { transactionDate, ...rest } = data;

    const [updated] = await db
      .update(transactions)
      .set({
        ...rest,
        ...(transactionDate ? { createdAt: new Date(transactionDate) } : {}),
        updatedAt: new Date(),
      })
      .where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId), liveTx))
      .returning();

    return updated;
  }

  /**
   * Move a transaction to the Trash, together with its live holds, in one DB transaction.
   * Both get the same `deleted_at`, which is how `restore` knows which holds went with it.
   * Already in the Trash → no-op (a double tap must not error).
   */
  async delete(userId: string, transactionId: string): Promise<{ holdIds: string[] }> {
    return db.transaction(async (tx) => {
      const mine = and(eq(transactions.id, transactionId), eq(transactions.userId, userId));
      const [row] = await tx.select({ deletedAt: transactions.deletedAt }).from(transactions).where(mine);
      if (!row) throw new NotFoundError('Transaction not found');
      if (row.deletedAt) return { holdIds: [] };

      const now = new Date();
      await tx.update(transactions).set({ deletedAt: now }).where(mine);
      const moved = await tx
        .update(holds)
        .set({ deletedAt: now })
        .where(and(eq(holds.transactionId, transactionId), eq(holds.userId, userId), liveHold))
        .returning({ id: holds.id });
      return { holdIds: moved.map((h) => h.id) };
    });
  }

  /** Bring a transaction back, plus only the holds that were deleted WITH it (same deleted_at). */
  async restore(userId: string, transactionId: string) {
    return db.transaction(async (tx) => {
      const mine = and(eq(transactions.id, transactionId), eq(transactions.userId, userId));
      // trash-aware: this read must see deleted rows
      const [row] = await tx.select().from(transactions).where(mine);
      if (!row) throw new NotFoundError('Transaction not found');
      if (!row.deletedAt) return { transaction: row, holds: [] };

      // Compared in SQL against the parent's own value, so no timestamp round-trip through JS.
      const restoredHolds = await tx
        .update(holds)
        .set({ deletedAt: null })
        .where(
          and(
            eq(holds.transactionId, transactionId),
            eq(holds.userId, userId),
            sql`${holds.deletedAt} = (SELECT deleted_at FROM transactions WHERE id = ${transactionId})`,
          ),
        )
        .returning();
      const [transaction] = await tx.update(transactions).set({ deletedAt: null, updatedAt: new Date() }).where(mine).returning();
      return { transaction, holds: restoredHolds };
    });
  }
}

export const transactionService = new TransactionService();
