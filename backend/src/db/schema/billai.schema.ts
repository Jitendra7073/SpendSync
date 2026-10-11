import { pgTable, uuid, text, integer, jsonb, timestamp, date, index, primaryKey } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';
import { transactions } from './transactions.schema';

export const billReadStatuses = ['queued', 'done', 'failed', 'no_consent'] as const;
export type BillReadStatus = (typeof billReadStatuses)[number];

/** What the AI read from a transaction's bill pages. One row per transaction; re-read when pages_key changes. */
export const billReads = pgTable(
  'bill_reads',
  {
    transactionId: uuid('transaction_id').primaryKey().references(() => transactions.id, { onDelete: 'cascade' }),
    userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
    status: text('status', { enum: billReadStatuses }).notNull().default('queued'),
    pagesKey: text('pages_key').notNull(),
    data: jsonb('data'),
    model: text('model'),
    attempts: integer('attempts').notNull().default(0),
    error: text('error'),
    createdAt: timestamp('created_at').notNull().defaultNow(),
    updatedAt: timestamp('updated_at').notNull().defaultNow(),
  },
  (t) => ({ byStatus: index('bill_reads_status_idx').on(t.status, t.updatedAt) }),
);
export type BillReadRow = typeof billReads.$inferSelect;

/** AI bill reads per user per UTC day (auto, retries and pre-fill all count). */
export const billReadCounts = pgTable(
  'bill_read_counts',
  {
    userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
    day: date('day', { mode: 'string' }).notNull(),
    count: integer('count').notNull().default(0),
  },
  (t) => ({ pk: primaryKey({ columns: [t.userId, t.day] }) }),
);
