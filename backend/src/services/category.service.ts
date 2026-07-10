import { eq, and } from 'drizzle-orm';
import { db } from '../db/index';
import { categories } from '../db/schema/index';
import type { CreateCategoryInput, UpdateCategoryInput } from '../types/category.types';
import { NotFoundError, ConflictError } from '../utils/errors';

/**
 * Category Service
 * Handles category management and auto-categorization logic
 */
export class CategoryService {
  /**
   * Create a new category rule
   */
  async create(userId: string, data: CreateCategoryInput) {
    // Check if keyword already exists for this user
    const existing = await db
      .select()
      .from(categories)
      .where(and(eq(categories.userId, userId), eq(categories.keyword, data.keyword)));

    if (existing.length > 0) {
      throw new ConflictError('Category rule with this keyword already exists');
    }

    const [category] = await db
      .insert(categories)
      .values({
        userId,
        keyword: data.keyword,
        category: data.category,
      })
      .returning();

    return category;
  }

  /**
   * Get all category rules for a user
   */
  async getAll(userId: string) {
    return await db
      .select()
      .from(categories)
      .where(eq(categories.userId, userId))
      .orderBy(categories.category);
  }

  /**
   * Get a single category rule by ID
   */
  async getById(userId: string, categoryId: string) {
    const [category] = await db
      .select()
      .from(categories)
      .where(and(eq(categories.id, categoryId), eq(categories.userId, userId)));

    if (!category) {
      throw new NotFoundError('Category rule not found');
    }

    return category;
  }

  /**
   * Update a category rule
   */
  async update(userId: string, categoryId: string, data: UpdateCategoryInput) {
    // Check if category exists and belongs to user
    await this.getById(userId, categoryId);

    const [updated] = await db
      .update(categories)
      .set({
        ...data,
        updatedAt: new Date(),
      })
      .where(and(eq(categories.id, categoryId), eq(categories.userId, userId)))
      .returning();

    return updated;
  }

  /**
   * Delete a category rule
   */
  async delete(userId: string, categoryId: string) {
    // Check if category exists and belongs to user
    await this.getById(userId, categoryId);

    await db
      .delete(categories)
      .where(and(eq(categories.id, categoryId), eq(categories.userId, userId)));
  }

  /**
   * Auto-suggest category based on merchant name
   */
  async suggestCategory(userId: string, merchantName: string): Promise<string | null> {
    const userCategories = await this.getAll(userId);
    const lowerMerchant = merchantName.toLowerCase();

    // Find matching keyword
    const match = userCategories.find((cat) => lowerMerchant.includes(cat.keyword));

    return match ? match.category : null;
  }
}

export const categoryService = new CategoryService();
