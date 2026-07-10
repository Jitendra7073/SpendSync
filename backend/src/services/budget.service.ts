import { eq, and } from 'drizzle-orm';
import { db } from '../db/index';
import { budgets } from '../db/schema/index';
import type { CreateBudgetInput, UpdateBudgetInput, BudgetQuery } from '../types/budget.types';
import { NotFoundError, ConflictError } from '../utils/errors';

/**
 * Budget Service
 * Handles budget management
 */
export class BudgetService {
  /**
   * Create a new budget
   */
  async create(userId: string, data: CreateBudgetInput) {
    // Check if budget already exists for this category and month
    const existing = await db
      .select()
      .from(budgets)
      .where(
        and(
          eq(budgets.userId, userId),
          eq(budgets.category, data.category),
          eq(budgets.month, data.month)
        )
      );

    if (existing.length > 0) {
      throw new ConflictError('Budget already exists for this category and month');
    }

    const [budget] = await db
      .insert(budgets)
      .values({
        userId,
        category: data.category,
        month: data.month,
        limitAmount: data.limitAmount,
      })
      .returning();

    return budget;
  }

  /**
   * Get budgets for a user with filters
   */
  async getAll(userId: string, query: BudgetQuery) {
    const { month, category } = query;

    // Build where conditions
    const conditions = [eq(budgets.userId, userId)];

    if (month) {
      conditions.push(eq(budgets.month, month));
    }

    if (category) {
      conditions.push(eq(budgets.category, category));
    }

    return await db
      .select()
      .from(budgets)
      .where(and(...conditions))
      .orderBy(budgets.month, budgets.category);
  }

  /**
   * Get a single budget by ID
   */
  async getById(userId: string, budgetId: string) {
    const [budget] = await db
      .select()
      .from(budgets)
      .where(and(eq(budgets.id, budgetId), eq(budgets.userId, userId)));

    if (!budget) {
      throw new NotFoundError('Budget not found');
    }

    return budget;
  }

  /**
   * Update a budget
   */
  async update(userId: string, budgetId: string, data: UpdateBudgetInput) {
    // Check if budget exists and belongs to user
    await this.getById(userId, budgetId);

    const [updated] = await db
      .update(budgets)
      .set({
        ...data,
        updatedAt: new Date(),
      })
      .where(and(eq(budgets.id, budgetId), eq(budgets.userId, userId)))
      .returning();

    return updated;
  }

  /**
   * Delete a budget
   */
  async delete(userId: string, budgetId: string) {
    // Check if budget exists and belongs to user
    await this.getById(userId, budgetId);

    await db
      .delete(budgets)
      .where(and(eq(budgets.id, budgetId), eq(budgets.userId, userId)));
  }
}

export const budgetService = new BudgetService();
