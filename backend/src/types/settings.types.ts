import { z } from 'zod';

/**
 * Settings update schema
 */
export const updateSettingsSchema = z.object({
  developerMode: z.boolean().optional(),
  emailNotifications: z.boolean().optional(),
  darkMode: z.boolean().optional(),
  pushNotifications: z.boolean().optional(),
  autoBackup: z.boolean().optional(),
  accentColor: z.string().min(1).max(50).optional(),
  language: z.string().min(1).max(50).optional(),
  currency: z.string().min(1).max(10).optional(),
  dateFormat: z.string().min(1).max(50).optional(),
});

/**
 * Developer mode toggle schema
 */
export const developerModeSchema = z.object({
  enabled: z.boolean(),
});

// Type exports
export type UpdateSettingsInput = z.infer<typeof updateSettingsSchema>;
export type DeveloperModeInput = z.infer<typeof developerModeSchema>;
