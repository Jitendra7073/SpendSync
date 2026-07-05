import { pgTable, uuid, text, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema.js';

/**
 * Categories table
 * Stores user-defined category keywords for automatic transaction categorization
 * Each user can customize their own merchant-to-category mappings
 */
export const categories = pgTable('categories', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  
  // Categorization rules
  keyword: text('keyword').notNull(), // Lowercase merchant keyword to match (e.g., "swiggy", "uber")
  category: text('category').notNull(), // Category name to assign (e.g., "Food", "Transport")
  
  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type Category = typeof categories.$inferSelect;
export type NewCategory = typeof categories.$inferInsert;
