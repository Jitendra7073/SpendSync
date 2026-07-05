import { Response, NextFunction } from 'express';
import { AuthRequest } from '../middleware/auth.middleware.js';
import { budgetService } from '../services/budget.service.js';
import { sendSuccess, sendCreated, sendNoContent } from '../utils/response.js';
import type { CreateBudgetInput, UpdateBudgetInput, BudgetQuery } from '../types/budget.types.js';

/**
 * Budget Controller
 * Handles HTTP requests for budget operations
 */
export class BudgetController {
  /**
   * Create a new budget
   * POST /budgets
   */
  async create(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const data: CreateBudgetInput = req.body;

      const budget = await budgetService.create(userId, data);
      sendCreated(res, budget);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get all budgets
   * GET /budgets
   */
  async getAll(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const query: BudgetQuery = req.query as any;

      const budgets = await budgetService.getAll(userId, query);
      sendSuccess(res, budgets);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get budget by ID
   * GET /budgets/:id
   */
  async getById(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;

      const budget = await budgetService.getById(userId, id);
      sendSuccess(res, budget);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Update budget
   * PATCH /budgets/:id
   */
  async update(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;
      const data: UpdateBudgetInput = req.body;

      const budget = await budgetService.update(userId, id, data);
      sendSuccess(res, budget);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Delete budget
   * DELETE /budgets/:id
   */
  async delete(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;

      await budgetService.delete(userId, id);
      sendNoContent(res);
    } catch (error) {
      next(error);
    }
  }
}

export const budgetController = new BudgetController();
