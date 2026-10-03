import { pgTable, uuid, text, integer, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';

/**
 * One row per "forgot password" code that was emailed. Only a keyed hash of the code is stored, never the
 * code itself, so a database leak cannot be used to reset anyone's password.
 */
export const passwordResets = pgTable('password_resets', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  email: text('email').notNull(),
  codeHash: text('code_hash').notNull(),
  attempts: integer('attempts').notNull().default(0),
  expiresAt: timestamp('expires_at').notNull(),
  usedAt: timestamp('used_at'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});
