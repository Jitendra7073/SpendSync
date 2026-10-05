/**
 * Complete schema definitions for Drizzle Kit
 * All tables defined inline to avoid module resolution issues
 */
import { pgTable, text, timestamp, boolean, uuid, decimal, integer, unique } from 'drizzle-orm/pg-core';

// ============================================================================
// AUTH TABLES (Better Auth)
// ============================================================================

export const user = pgTable('user', {
  id: text('id').primaryKey(),
  email: text('email').notNull().unique(),
  emailVerified: boolean('emailVerified').notNull().default(false),
  name: text('name'),
  image: text('image'),
  createdAt: timestamp('createdAt').notNull().defaultNow(),
  updatedAt: timestamp('updatedAt').notNull().defaultNow(),
});

export const session = pgTable('session', {
  id: text('id').primaryKey(),
  userId: text('userId')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  expiresAt: timestamp('expiresAt').notNull(),
  token: text('token').notNull().unique(),
  ipAddress: text('ipAddress'),
  userAgent: text('userAgent'),
  createdAt: timestamp('createdAt').notNull().defaultNow(),
  updatedAt: timestamp('updatedAt').notNull().defaultNow(),
});

export const account = pgTable('account', {
  id: text('id').primaryKey(),
  userId: text('userId')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  accountId: text('accountId').notNull(),
  providerId: text('providerId').notNull(),
  accessToken: text('accessToken'),
  refreshToken: text('refreshToken'),
  idToken: text('idToken'),
  accessTokenExpiresAt: timestamp('accessTokenExpiresAt'),
  refreshTokenExpiresAt: timestamp('refreshTokenExpiresAt'),
  scope: text('scope'),
  expiresAt: timestamp('expiresAt'),
  password: text('password'),
  createdAt: timestamp('createdAt').notNull().defaultNow(),
  updatedAt: timestamp('updatedAt').notNull().defaultNow(),
});

export const verification = pgTable('verification', {
  id: text('id').primaryKey(),
  identifier: text('identifier').notNull(),
  value: text('value').notNull(),
  expiresAt: timestamp('expiresAt').notNull(),
  createdAt: timestamp('createdAt').notNull().defaultNow(),
  updatedAt: timestamp('updatedAt').notNull().defaultNow(),
});

// ============================================================================
// APPLICATION TABLES
// ============================================================================

export const transactions = pgTable('transactions', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull(),
  type: text('type', { enum: ['debit', 'credit'] }).notNull(),
  merchant: text('merchant').notNull(),
  category: text('category').notNull(),
  sourceApp: text('source_app'),
  note: text('note'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

export const categories = pgTable('categories', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  keyword: text('keyword').notNull(),
  category: text('category').notNull(),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

export const budgets = pgTable('budgets', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  category: text('category').notNull(),
  month: text('month').notNull(),
  limitAmount: decimal('limit_amount', { precision: 12, scale: 2 }).notNull(),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
  // Planify: a budget row is a "bucket" of the month's plan.
  kind: text('kind').notNull().default('spend'), // 'fixed' | 'spend' | 'savings'
  name: text('name'), // display name; `category` stays the transaction category it tracks
  sortOrder: integer('sort_order').notNull().default(0),
  rollover: boolean('rollover').notNull().default(false),
});

export const holds = pgTable('holds', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  transactionId: uuid('transaction_id')
    .notNull()
    .references(() => transactions.id, { onDelete: 'cascade' }),
  direction: text('direction', { enum: ['owed_to_me', 'owed_by_me'] }).notNull(),
  personName: text('person_name').notNull(),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull(),
  expectedReturnDate: timestamp('expected_return_date').notNull(),
  status: text('status', { enum: ['pending', 'settled'] }).notNull().default('pending'),
  settledAt: timestamp('settled_at'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// ============================================================================
// USER SETTINGS TABLE
// ============================================================================

export const userSettings = pgTable('user_settings', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .unique()
    .references(() => user.id, { onDelete: 'cascade' }),
  developerMode: boolean('developer_mode').notNull().default(false),
  emailNotifications: boolean('email_notifications').notNull().default(true),
  darkMode: boolean('dark_mode').notNull().default(false),
  pushNotifications: boolean('push_notifications').notNull().default(true),
  autoBackup: boolean('auto_backup').notNull().default(true),
  accentColor: text('accent_color').notNull().default('Brand Blue'),
  language: text('language').notNull().default('English'),
  currency: text('currency').notNull().default('USD'),
  dateFormat: text('date_format').notNull().default('DD / MM / YYYY'),

  // Theme + privacy + automation prefs. The PIN itself is never stored here —
  // only whether masking is on and for how long amounts stay revealed.
  themeMode: text('theme_mode').notNull().default('System'),
  amountMaskingEnabled: boolean('amount_masking_enabled').notNull().default(false),
  amountVisibilitySeconds: integer('amount_visibility_seconds').notNull().default(60),
  autoCaptureEnabled: boolean('auto_capture_enabled').notNull().default(false),
  // Comma-separated Android package names the user allowed for auto-capture.
  autoCapturePackages: text('auto_capture_packages').notNull().default(''),
  // Assistant preferences (Settings -> Assistant). Consent to use the assistant stays device-local.
  assistantModel: text('assistant_model').notNull().default('auto'),
  assistantStyle: text('assistant_style').notNull().default('balanced'),
  assistantTone: text('assistant_tone').notNull().default('friendly'),
  assistantInstructions: text('assistant_instructions').notNull().default(''),
  // Comma-separated tool names the user switched off.
  assistantDisabledTools: text('assistant_disabled_tools').notNull().default(''),

  // Planify (Settings -> Planify): limit alerts, evening summary, smallest credit that triggers "plan this month?".
  planifyAlerts: boolean('planify_alerts').notNull().default(true),
  planifyDaily: boolean('planify_daily').notNull().default(false),
  planifySalaryMin: integer('planify_salary_min').notNull().default(5000),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// ============================================================================
// ASSISTANT AUDIT
// ============================================================================

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
  status: text('status').notNull().default('open'), // open | in_progress | resolved
  emailStatus: text('email_status').notNull().default('pending'), // pending | sent | failed | not_configured
  emailError: text('email_error'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

// ============================================================================
// PASSWORD RESET CODES
// ============================================================================

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

// ============================================================================
// PLANIFY: monthly plans and the log of moves between buckets
// ============================================================================

export const plans = pgTable(
  'plans',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: text('user_id')
      .notNull()
      .references(() => user.id, { onDelete: 'cascade' }),
    month: text('month').notNull(),
    income: decimal('income', { precision: 12, scale: 2 }).notNull().default('0'),
    carryOver: decimal('carry_over', { precision: 12, scale: 2 }).notNull().default('0'),
    status: text('status').notNull().default('active'),
    createdAt: timestamp('created_at').notNull().defaultNow(),
    updatedAt: timestamp('updated_at').notNull().defaultNow(),
  },
  (t) => ({ onePerMonth: unique('plans_user_month_unique').on(t.userId, t.month) }),
);

export const planEvents = pgTable('plan_events', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  month: text('month').notNull(),
  kind: text('kind').notNull(),
  fromCategory: text('from_category'),
  toCategory: text('to_category'),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull().default('0'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

/** What the user told Planify about which spending belongs in a bucket (Yes/No on a suggestion). */
export const planMatches = pgTable(
  'plan_matches',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: text('user_id')
      .notNull()
      .references(() => user.id, { onDelete: 'cascade' }),
    bucket: text('bucket').notNull(), // the bucket's category
    kind: text('kind').notNull(), // 'category' | 'merchant'
    key: text('key').notNull(), // normalised category or merchant name
    label: text('label').notNull(), // as the user knows it
    verdict: text('verdict').notNull(), // 'yes' | 'no'
    createdAt: timestamp('created_at').notNull().defaultNow(),
  },
  (t) => ({ onePerPair: unique('plan_matches_unique').on(t.userId, t.bucket, t.kind, t.key) }),
);
