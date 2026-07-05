import { Response, NextFunction } from 'express';
import { AuthRequest } from '../middleware/auth.middleware.js';
import { settingsService } from '../services/settings.service.js';
import { sendSuccess } from '../utils/response.js';
import type { UpdateSettingsInput, DeveloperModeInput } from '../types/settings.types.js';

/**
 * Settings Controller
 * Handles HTTP requests for user settings
 */
export class SettingsController {
  /**
   * Get current user settings
   * GET /settings
   */
  async getSettings(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const settings = await settingsService.getSettings(userId);
      sendSuccess(res, settings);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Update user settings
   * PATCH /settings
   */
  async updateSettings(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const data: UpdateSettingsInput = req.body;

      const settings = await settingsService.updateSettings(userId, data);
      sendSuccess(res, settings);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Toggle developer mode
   * POST /settings/developer-mode
   */
  async toggleDeveloperMode(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { enabled }: DeveloperModeInput = req.body;

      const settings = enabled
        ? await settingsService.enableDeveloperMode(userId)
        : await settingsService.disableDeveloperMode(userId);

      sendSuccess(res, {
        message: `Developer mode ${enabled ? 'enabled' : 'disabled'}`,
        settings,
      });
    } catch (error) {
      next(error);
    }
  }

  /**
   * Check developer mode status
   * GET /settings/developer-mode
   */
  async getDeveloperMode(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const isDeveloperMode = await settingsService.isDeveloperMode(userId);

      sendSuccess(res, {
        developerMode: isDeveloperMode,
      });
    } catch (error) {
      next(error);
    }
  }
}

export const settingsController = new SettingsController();
