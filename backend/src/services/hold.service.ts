import { eq, and, desc } from 'drizzle-orm';
import { liveHold } from '../lib/live';
import { db } from '../db/index';
import { holds } from '../db/schema/index';
import type { CreateHoldInput, UpdateHoldInput, HoldQuery } from '../types/hold.types';
import { NotFoundError } from '../utils/errors';

export class HoldService {
  async create(userId: string, data: CreateHoldInput) {
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

  async delete(userId: string, holdId: string) {
    await this.getById(userId, holdId);

    await db.delete(holds).where(and(eq(holds.id, holdId), eq(holds.userId, userId)));
  }
}

export const holdService = new HoldService();
