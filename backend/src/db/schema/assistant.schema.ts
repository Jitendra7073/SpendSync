import { pgTable, uuid, text, boolean, integer, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';

/**
 * One row per tool the assistant ran for a user. Today only reads/navigation run, but the same table
 * will record every write (with its undo state) once the assistant can change data, so the user can
 * always see what it did. Never stores tool arguments or results — only what ran and how it went.
 */
export const assistantAudit = pgTable('assistant_audit', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  conversationId: text('conversation_id').notNull(),
  toolName: text('tool_name').notNull(),
  tier: text('tier').notNull(),
  ok: boolean('ok').notNull(),
  durationMs: integer('duration_ms').notNull(),
  error: text('error'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

export type AssistantAuditRow = typeof assistantAudit.$inferSelect;
