import { pgTable, uuid, text, integer, smallint, bigint, timestamp, date, index, unique, primaryKey } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';
import { transactions } from './transactions.schema';

export const billStatuses = ['pending', 'ready'] as const;
export type BillStatus = (typeof billStatuses)[number];

/** A bill page attached to a transaction; the file lives in Cloudinary under `public_id`. */
export const bills = pgTable(
  'bills',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
    transactionId: uuid('transaction_id').notNull().references(() => transactions.id, { onDelete: 'cascade' }),
    publicId: text('public_id').notNull().unique(),
    position: smallint('position').notNull(),
    status: text('status', { enum: billStatuses }).notNull().default('pending'),
    format: text('format'),
    bytes: integer('bytes'),
    width: integer('width'),
    height: integer('height'),
    pages: integer('pages'),
    version: bigint('version', { mode: 'number' }),
    clientKey: uuid('client_key').notNull(),
    // Set when this upload replaces a page: that page goes to the Trash when this one is confirmed.
    replacesId: uuid('replaces_id'),
    createdAt: timestamp('created_at').notNull().defaultNow(),
    updatedAt: timestamp('updated_at').notNull().defaultNow(),
    deletedAt: timestamp('deleted_at'),
  },
  (t) => ({
    byTransaction: index('bills_transaction_idx').on(t.transactionId),
    userDeleted: index('bills_user_deleted_idx').on(t.userId, t.deletedAt),
    clientKey: unique('bills_user_client_key_unique').on(t.userId, t.clientKey),
  }),
);

export type Bill = typeof bills.$inferSelect;
export type NewBill = typeof bills.$inferInsert;

/** Outbox of Cloudinary deletions. No FK on purpose: it must outlive the rows it was cut from. */
export const billPurges = pgTable('bill_purges', {
  id: uuid('id').primaryKey().defaultRandom(),
  target: text('target').notNull(),
  kind: text('kind', { enum: ['asset', 'prefix'] }).notNull().default('asset'),
  attempts: integer('attempts').notNull().default(0),
  nextAt: timestamp('next_at').notNull().defaultNow(),
  lastError: text('last_error'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

/** Uploads reserved per user per UTC day. Deleting bills never lowers it. */
export const billUploadCounts = pgTable(
  'bill_upload_counts',
  {
    userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
    day: date('day', { mode: 'string' }).notNull(),
    count: integer('count').notNull().default(0),
  },
  (t) => ({ pk: primaryKey({ columns: [t.userId, t.day] }) }),
);
