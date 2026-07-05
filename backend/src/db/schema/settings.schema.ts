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
  
  // Timestamps
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

// Type exports
export type UserSettings = typeof userSettings.$inferSelect;
export type NewUserSettings = typeof userSettings.$inferInsert;
