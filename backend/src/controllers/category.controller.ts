import { Response, NextFunction } from 'express';
import { AuthRequest } from '../middleware/auth.middleware.js';
import { categoryService } from '../services/category.service.js';
import { sendSuccess, sendCreated, sendNoContent } from '../utils/response.js';
import type { CreateCategoryInput, UpdateCategoryInput } from '../types/category.types.js';

/**
 * Category Controller
 * Handles HTTP requests for category operations
 */
export class CategoryController {
  /**
   * Create a new category rule
   * POST /categories
   */
  async create(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const data: CreateCategoryInput = req.body;

      const category = await categoryService.create(userId, data);
      sendCreated(res, category);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get all category rules
   * GET /categories
   */
  async getAll(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;

      const categories = await categoryService.getAll(userId);
      sendSuccess(res, categories);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get category by ID
   * GET /categories/:id
   */
  async getById(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;

      const category = await categoryService.getById(userId, id);
      sendSuccess(res, category);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Update category rule
   * PATCH /categories/:id
   */
  async update(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;
      const data: UpdateCategoryInput = req.body;

      const category = await categoryService.update(userId, id, data);
      sendSuccess(res, category);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Delete category rule
   * DELETE /categories/:id
   */
  async delete(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;

      await categoryService.delete(userId, id);
      sendNoContent(res);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Suggest category for a merchant
   * POST /categories/suggest
   */
  async suggest(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { merchant } = req.body;

      const category = await categoryService.suggestCategory(userId, merchant);
      sendSuccess(res, { merchant, suggestedCategory: category });
    } catch (error) {
      next(error);
    }
  }
}

export const categoryController = new CategoryController();
