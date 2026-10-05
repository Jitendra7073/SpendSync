import { pgTable, uuid, text, decimal, timestamp, integer, boolean, unique } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';

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

  // Planify: a budget row is a "bucket" of the month's plan.
  kind: text('kind').notNull().default('spend'), // 'fixed' | 'spend' | 'savings'
  name: text('name'), // display name; `category` stays the transaction category it tracks
  sortOrder: integer('sort_order').notNull().default(0),
  rollover: boolean('rollover').notNull().default(false),

  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type Budget = typeof budgets.$inferSelect;
export type NewBudget = typeof budgets.$inferInsert;

/**
 * One row per user per month: how much money the user decided to plan (Planify). The buckets themselves
 * are the `budgets` rows of that month.
 */
export const plans = pgTable(
  'plans',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: text('user_id')
      .notNull()
      .references(() => user.id, { onDelete: 'cascade' }),
    month: text('month').notNull(), // YYYY-MM
    income: decimal('income', { precision: 12, scale: 2 }).notNull().default('0'),
    carryOver: decimal('carry_over', { precision: 12, scale: 2 }).notNull().default('0'),
    status: text('status').notNull().default('active'), // active | closed
    createdAt: timestamp('created_at').notNull().defaultNow(),
    updatedAt: timestamp('updated_at').notNull().defaultNow(),
  },
  (t) => ({ onePerMonth: unique('plans_user_month_unique').on(t.userId, t.month) }),
);

/** A log of money moved between buckets, so the month-end review can tell what shifted. */
export const planEvents = pgTable('plan_events', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  month: text('month').notNull(),
  kind: text('kind').notNull(), // 'move'
  fromCategory: text('from_category'),
  toCategory: text('to_category'),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull().default('0'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

export type Plan = typeof plans.$inferSelect;
