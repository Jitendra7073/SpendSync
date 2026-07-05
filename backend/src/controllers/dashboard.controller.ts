import { Response, NextFunction } from 'express';
import { AuthRequest } from '../middleware/auth.middleware.js';
import { dashboardService } from '../services/dashboard.service.js';
import { sendSuccess } from '../utils/response.js';

/**
 * Dashboard Controller
 * Handles HTTP requests for dashboard and analytics
 */
export class DashboardController {
  /**
   * Get dashboard summary
   * GET /dashboard/summary?month=YYYY-MM
   */
  async getSummary(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const month = (req.query.month as string) || new Date().toISOString().slice(0, 7);

      const summary = await dashboardService.getSummary(userId, month);
      sendSuccess(res, summary);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get monthly trend
   * GET /dashboard/trend?months=6
   */
  async getMonthlyTrend(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const months = parseInt(req.query.months as string) || 6;

      const trend = await dashboardService.getMonthlyTrend(userId, months);
      sendSuccess(res, trend);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get top merchants
   * GET /dashboard/top-merchants?limit=10
   */
  async getTopMerchants(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const limit = parseInt(req.query.limit as string) || 10;

      const merchants = await dashboardService.getTopMerchants(userId, limit);
      sendSuccess(res, merchants);
    } catch (error) {
      next(error);
    }
  }
}

export const dashboardController = new DashboardController();
