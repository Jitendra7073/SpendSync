import { Router } from 'express';
import { dashboardController } from '../controllers/dashboard.controller.js';
import { authenticate } from '../middleware/auth.middleware.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

/**
 * @route   GET /api/dashboard/summary
 * @desc    Get dashboard summary for a month
 * @access  Private
 */
router.get('/summary', dashboardController.getSummary.bind(dashboardController));

/**
 * @route   GET /api/dashboard/trend
 * @desc    Get monthly spending trend
 * @access  Private
 */
router.get('/trend', dashboardController.getMonthlyTrend.bind(dashboardController));

/**
 * @route   GET /api/dashboard/top-merchants
 * @desc    Get top merchants by spending
 * @access  Private
 */
router.get('/top-merchants', dashboardController.getTopMerchants.bind(dashboardController));

export default router;
