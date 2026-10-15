import { eq, and, desc } from 'drizzle-orm';
import { db } from '../db/index';
import { holds, transactions } from '../db/schema/index';
import { liveHold } from '../lib/live';
import { assertParentLive } from '../trash/rules';
import { transactionService } from './transaction.service';
import type { CreateHoldInput, UpdateHoldInput, HoldQuery } from '../types/hold.types';
import { NotFoundError } from '../utils/errors';

export class HoldService {
  async create(userId: string, data: CreateHoldInput) {
    // The transaction must be the user's own and not in the Trash (404 otherwise).
    await transactionService.getById(userId, data.transactionId);

    const [hold] = await db
      .insert(holds)
      .values({
        userId,
        transactionId: data.transactionId,
        direction: data.direction,
        personName: data.personName,
        amount: data.amount,
        expectedReturnDate: new Date(data.expectedReturnDate),
      })
      .returning();

    return hold;
  }

  async getAll(userId: string, query: HoldQuery) {
    const conditions = [eq(holds.userId, userId), liveHold];
    if (query.status) conditions.push(eq(holds.status, query.status));
    if (query.direction) conditions.push(eq(holds.direction, query.direction));

    return db
      .select()
      .from(holds)
      .where(and(...conditions))
      .orderBy(desc(holds.createdAt));
  }

  async getById(userId: string, holdId: string) {
    const [hold] = await db
      .select()
      .from(holds)
      .where(and(eq(holds.id, holdId), eq(holds.userId, userId), liveHold));

    if (!hold) {
      throw new NotFoundError('Hold not found');
    }

    return hold;
  }

  async update(userId: string, holdId: string, data: UpdateHoldInput) {
    // Check it exists and belongs to this user before writing.
    await this.getById(userId, holdId);

    const { expectedReturnDate, status, ...rest } = data;

    const [updated] = await db
      .update(holds)
      .set({
        ...rest,
        ...(expectedReturnDate ? { expectedReturnDate: new Date(expectedReturnDate) } : {}),
        ...(status ? { status, settledAt: status === 'settled' ? new Date() : null } : {}),
        updatedAt: new Date(),
      })
      .where(and(eq(holds.id, holdId), eq(holds.userId, userId), liveHold))
      .returning();

    return updated;
  }

  /** Move one hold to the Trash. Already there → no-op. */
  async delete(userId: string, holdId: string) {
    const mine = and(eq(holds.id, holdId), eq(holds.userId, userId));
    const [row] = await db.select({ deletedAt: holds.deletedAt }).from(holds).where(mine);
    if (!row) throw new NotFoundError('Hold not found');
    if (row.deletedAt) return;
    await db.update(holds).set({ deletedAt: new Date() }).where(mine);
  }

  /** Bring one hold back. Refused while its transaction is in the Trash (restore that instead). */
  async restore(userId: string, holdId: string) {
    const mine = and(eq(holds.id, holdId), eq(holds.userId, userId));
    const [row] = await db
      .select({ hold: holds, parentDeletedAt: transactions.deletedAt })
      .from(holds)
      .innerJoin(transactions, eq(transactions.id, holds.transactionId))
      .where(mine);
    if (!row) throw new NotFoundError('Hold not found');
    if (!row.hold.deletedAt) return row.hold;
    assertParentLive(row.parentDeletedAt);
    const [hold] = await db.update(holds).set({ deletedAt: null, updatedAt: new Date() }).where(mine).returning();
    return hold;
  }
}

export const holdService = new HoldService();
