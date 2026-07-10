import { pgTable, uuid, text, decimal, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';

/**
 * Transaction types enum
 */
export const transactionTypes = ['debit', 'credit'] as const;
export type TransactionType = (typeof transactionTypes)[number];

/**
 * Transactions table
 * Stores all user transactions captured from payment apps or manually entered
 */
export const transactions = pgTable('transactions', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  
  // Transaction details
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull(),
  type: text('type', { enum: transactionTypes }).notNull(),
  merchant: text('merchant').notNull(),
  category: text('category').notNull(),
  
  // Source information
  sourceApp: text('source_app'), // GPay, PhonePe, Paytm, etc.
  
  // Optional metadata
  note: text('note'),
  
  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type Transaction = typeof transactions.$inferSelect;
export type NewTransaction = typeof transactions.$inferInsert;
