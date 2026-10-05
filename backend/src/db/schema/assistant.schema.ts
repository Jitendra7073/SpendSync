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

// ============================================================================
// ASSISTANT FEEDBACK + SUPPORT TICKETS
// ============================================================================

/** Thumbs up/down on an assistant answer, with the reasons the user picked. Used to tune that user's answers. */
export const assistantFeedback = pgTable('assistant_feedback', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  conversationId: text('conversation_id').notNull(),
  messageRef: text('message_ref').notNull().default(''),
  rating: text('rating').notNull(), // 'up' | 'down'
  // Comma-separated reason codes (see FEEDBACK_REASONS in assistant/feedback.ts).
  reasons: text('reasons').notNull().default(''),
  comment: text('comment').notNull().default(''),
  question: text('question').notNull().default(''),
  answer: text('answer').notNull().default(''),
  model: text('model').notNull().default(''),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

/** A report the user sent to the support team from the assistant. The email is a copy; this row is the record. */
export const supportTickets = pgTable('support_tickets', {
  id: uuid('id').primaryKey().defaultRandom(),
  ref: text('ref').notNull().unique(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  category: text('category').notNull(),
  message: text('message').notNull(),
  // JSON: app version, device, screen, language, recent assistant chat (only if the user allowed it).
  context: text('context').notNull().default('{}'),
  status: text('status').notNull().default('open'), // open | in_progress | resolved | closed (closed by the user)
  emailStatus: text('email_status').notNull().default('pending'), // pending | sent | failed | not_configured
  emailError: text('email_error'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});
