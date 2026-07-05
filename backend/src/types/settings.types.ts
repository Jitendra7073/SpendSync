import { z } from 'zod';

/**
 * Settings update schema
 */
export const updateSettingsSchema = z.object({
  developerMode: z.boolean().optional(),
  emailNotifications: z.boolean().optional(),
  darkMode: z.boolean().optional(),
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
