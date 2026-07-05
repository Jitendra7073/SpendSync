import { pgTable, uuid, text, decimal, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema.js';

/**
 * Budgets table
 * Stores monthly budget limits per category for each user
 */
export const budgets = pgTable('budgets', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  
  // Budget configuration
  category: text('category').notNull(), // Must match a category used in transactions
  month: text('month').notNull(), // Format: YYYY-MM (e.g., "2026-07")
  limitAmount: decimal('limit_amount', { precision: 12, scale: 2 }).notNull(),
  
  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type Budget = typeof budgets.$inferSelect;
export type NewBudget = typeof budgets.$inferInsert;
