import { eq, and, desc, gte, lte, sql } from 'drizzle-orm';
import { db } from '../db/index.js';
import { transactions } from '../db/schema/index.js';
import type { CreateTransactionInput, UpdateTransactionInput, TransactionQuery } from '../types/transaction.types.js';
import { NotFoundError } from '../utils/errors.js';

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
    const conditions = [eq(transactions.userId, userId)];

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
      .where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId)));

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

    const [updated] = await db
      .update(transactions)
      .set({
        ...data,
        updatedAt: new Date(),
      })
      .where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId)))
      .returning();

    return updated;
  }

  /**
   * Delete a transaction
   */
  async delete(userId: string, transactionId: string) {
    // Check if transaction exists and belongs to user
    await this.getById(userId, transactionId);

    await db
      .delete(transactions)
      .where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId)));
  }
}

export const transactionService = new TransactionService();
