import { pgTable, uuid, text, decimal, timestamp, index } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';
import { transactions } from './transactions.schema';

/**
 * Bank SMS / email / UPI-app messages Smart capture saw (docs/superpowers/specs/2026-10-15-smart-capture-design.md).
 * `id` is made on the phone so an offline retry is stored once. `body` has long digit runs masked.
 * status: added | duplicate | review | ignored. On `review`, `transaction_id` is the suspected match (if any).
 */
export const capturedMessages = pgTable(
  'captured_messages',
  {
    id: uuid('id').primaryKey(),
    userId: text('user_id')
      .notNull()
      .references(() => user.id, { onDelete: 'cascade' }),
    source: text('source').notNull(), // 'sms' | 'email' | 'upi'
    sender: text('sender').notNull(),
    packageName: text('package_name'),
    body: text('body').notNull(), // '' when the user keeps no copies
    receivedAt: timestamp('received_at').notNull(),
    amount: decimal('amount', { precision: 12, scale: 2 }),
    type: text('type'), // 'debit' | 'credit'
    payee: text('payee'),
    refNo: text('ref_no'),
    last4: text('last4'),
    balance: decimal('balance', { precision: 14, scale: 2 }),
    status: text('status').notNull(),
    reason: text('reason'),
    transactionId: uuid('transaction_id').references(() => transactions.id, { onDelete: 'set null' }),
    duplicateOf: uuid('duplicate_of'),
    createdAt: timestamp('created_at').notNull().defaultNow(),
  },
  (t) => ({
    byTime: index('captured_messages_user_time_idx').on(t.userId, t.receivedAt),
    byRef: index('captured_messages_user_ref_idx').on(t.userId, t.refNo),
  }),
);

export type CapturedMessage = typeof capturedMessages.$inferSelect;
