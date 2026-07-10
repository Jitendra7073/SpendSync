import { eq } from 'drizzle-orm';
import { db } from '../db/index';
import { userSettings } from '../db/schema/index';

/**
 * Settings Service
 * Manages user application settings
 */
export class SettingsService {
  /**
   * Get or create settings for a user
   */
  async getSettings(userId: string) {
    const [settings] = await db
      .select()
      .from(userSettings)
      .where(eq(userSettings.userId, userId));

    // Create default settings if they don't exist
    if (!settings) {
      return this.createDefaultSettings(userId);
    }

    return settings;
  }

  /**
   * Create default settings for a user
   */
  async createDefaultSettings(userId: string) {
    const [settings] = await db
      .insert(userSettings)
      .values({
        userId,
        developerMode: false,
        emailNotifications: true,
        darkMode: false,
        pushNotifications: true,
        autoBackup: true,
        accentColor: 'Brand Blue',
        language: 'English',
        currency: 'USD',
        dateFormat: 'DD / MM / YYYY',
      })
      .returning();

    return settings;
  }

  /**
   * Update user settings
   */
  async updateSettings(
    userId: string,
    updates: {
      developerMode?: boolean;
      emailNotifications?: boolean;
      darkMode?: boolean;
      pushNotifications?: boolean;
      autoBackup?: boolean;
      accentColor?: string;
      language?: string;
      currency?: string;
      dateFormat?: string;
    }
  ) {
    // Ensure settings exist
    await this.getSettings(userId);

    const [updated] = await db
      .update(userSettings)
      .set({
        ...updates,
        updatedAt: new Date(),
      })
      .where(eq(userSettings.userId, userId))
      .returning();

    return updated;
  }

  /**
   * Enable developer mode for a user
   */
  async enableDeveloperMode(userId: string) {
    return this.updateSettings(userId, { developerMode: true });
  }

  /**
   * Disable developer mode for a user
   */
  async disableDeveloperMode(userId: string) {
    return this.updateSettings(userId, { developerMode: false });
  }

  /**
   * Check if user has developer mode enabled
   */
  async isDeveloperMode(userId: string): Promise<boolean> {
    const settings = await this.getSettings(userId);
    return settings.developerMode;
  }
}

export const settingsService = new SettingsService();
