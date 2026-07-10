import { pgTable, uuid, text, boolean, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema.js';

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

  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type UserSettings = typeof userSettings.$inferSelect;
export type NewUserSettings = typeof userSettings.$inferInsert;
