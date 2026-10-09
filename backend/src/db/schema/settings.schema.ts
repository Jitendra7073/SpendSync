import { pgTable, uuid, text, boolean, timestamp, integer } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';

/**
 * User Settings table
 * Stores user-specific application settings
 */
export const userSettings = pgTable('user_settings', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .unique()
    .references(() => user.id, { onDelete: 'cascade' }),
  
  // Developer mode - bypasses email verification
  developerMode: boolean('developer_mode').notNull().default(false),

  // Other settings can be added here
  emailNotifications: boolean('email_notifications').notNull().default(true),
  darkMode: boolean('dark_mode').notNull().default(false),

  // App personalisation — synced across devices for signed-in users.
  // Security-sensitive prefs (PIN, biometric lock) intentionally stay
  // device-local and are never sent to the backend.
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
  planifyFundingSource: text('planify_funding_source').notNull().default('detected_salary'), // 'detected_salary' | 'net_balance' | 'custom'
  planifyCustomAmount: integer('planify_custom_amount').notNull().default(0),

  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type UserSettings = typeof userSettings.$inferSelect;
export type NewUserSettings = typeof userSettings.$inferInsert;
