import { Router } from 'express';
import { settingsController } from '../controllers/settings.controller.js';
import { authenticate } from '../middleware/auth.middleware.js';
import { validate } from '../middleware/validate.middleware.js';
import { updateSettingsSchema, developerModeSchema } from '../types/settings.types.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

/**
 * @route   GET /api/settings
 * @desc    Get current user settings
 * @access  Private
 */
router.get('/', settingsController.getSettings.bind(settingsController));

/**
 * @route   PATCH /api/settings
 * @desc    Update user settings
 * @access  Private
 */
router.patch(
  '/',
  validate(updateSettingsSchema),
  settingsController.updateSettings.bind(settingsController)
);

/**
 * @route   GET /api/settings/developer-mode
 * @desc    Get developer mode status
 * @access  Private
 */
router.get('/developer-mode', settingsController.getDeveloperMode.bind(settingsController));

/**
 * @route   POST /api/settings/developer-mode
 * @desc    Toggle developer mode
 * @access  Private
 */
router.post(
  '/developer-mode',
  validate(developerModeSchema),
  settingsController.toggleDeveloperMode.bind(settingsController)
);

export default router;
