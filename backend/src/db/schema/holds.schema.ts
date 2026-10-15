import { pgTable, uuid, text, decimal, timestamp, index } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';
import { transactions } from './transactions.schema';

export const holdDirections = ['owed_to_me', 'owed_by_me'] as const;
export type HoldDirection = (typeof holdDirections)[number];

export const holdStatuses = ['pending', 'settled'] as const;
export type HoldStatus = (typeof holdStatuses)[number];

/**
 * Tracks money lent/borrowed alongside a transaction. `amount` is a
 * snapshot taken at creation — editing the linked transaction later never
 * retroactively changes what this hold expects back.
 */
export const holds = pgTable(
  'holds',
  {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  transactionId: uuid('transaction_id')
    .notNull()
    .references(() => transactions.id, { onDelete: 'cascade' }),
  direction: text('direction', { enum: holdDirections }).notNull(),
  personName: text('person_name').notNull(),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull(),
  expectedReturnDate: timestamp('expected_return_date').notNull(),
  status: text('status', { enum: holdStatuses }).notNull().default('pending'),
  settledAt: timestamp('settled_at'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
  // Set when moved to Trash (with its transaction, or on its own); null = live.
  deletedAt: timestamp('deleted_at'),
  },
  (t) => ({ userDeleted: index('holds_user_deleted_idx').on(t.userId, t.deletedAt) }),
);

export type Hold = typeof holds.$inferSelect;
export type NewHold = typeof holds.$inferInsert;
