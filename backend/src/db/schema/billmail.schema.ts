import { pgTable, text, integer, timestamp, date, primaryKey } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';

/** Bill emails sent per user per UTC day; last_at drives the once-a-minute limit. */
export const billEmailCounts = pgTable(
  'bill_email_counts',
  {
    userId: text('user_id').notNull().references(() => user.id, { onDelete: 'cascade' }),
    day: date('day', { mode: 'string' }).notNull(),
    count: integer('count').notNull().default(0),
    lastAt: timestamp('last_at').notNull().defaultNow(),
  },
  (t) => ({ pk: primaryKey({ columns: [t.userId, t.day] }) }),
);
