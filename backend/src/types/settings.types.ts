import { z } from 'zod';

/**
 * Settings update schema
 */
export const SUPPORTED_LANGUAGES = ['English', 'Hindi', 'Spanish', 'French', 'German'] as const;
export const THEME_MODES = ['System', 'Light', 'Dark'] as const;
export const ACCENT_COLORS = [
  'Brand Blue',
  'Emerald Green',
  'Kakariki Green',
  'Crimson Red',
  'Coral Orange',
] as const;
export const DATE_FORMATS = ['DD / MM / YYYY', 'MM / DD / YYYY', 'YYYY - MM - DD'] as const;

export const ASSISTANT_STYLES = ['short', 'balanced', 'detailed'] as const;
export const ASSISTANT_TONES = ['simple', 'friendly', 'professional'] as const;

export const updateSettingsSchema = z.object({
  developerMode: z.boolean().optional(),
  emailNotifications: z.boolean().optional(),
  darkMode: z.boolean().optional(),
  pushNotifications: z.boolean().optional(),
  autoBackup: z.boolean().optional(),
  accentColor: z.enum(ACCENT_COLORS).optional(),
  language: z.enum(SUPPORTED_LANGUAGES).optional(),
  currency: z.string().min(1).max(10).optional(),
  dateFormat: z.enum(DATE_FORMATS).optional(),
  themeMode: z.enum(THEME_MODES).optional(),
  amountMaskingEnabled: z.boolean().optional(),
  amountVisibilitySeconds: z.number().int().min(10).max(3600).optional(),
  autoCaptureEnabled: z.boolean().optional(),
  autoCapturePackages: z.string().max(2000).optional(),
  assistantModel: z.string().min(1).max(100).optional(),
  assistantStyle: z.enum(ASSISTANT_STYLES).optional(),
  assistantTone: z.enum(ASSISTANT_TONES).optional(),
  assistantInstructions: z.string().max(300).optional(),
  assistantDisabledTools: z.string().max(600).optional(),
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
