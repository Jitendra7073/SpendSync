import { Response, NextFunction } from 'express';
import { AuthRequest } from '../middleware/auth.middleware.js';
import { transactionService } from '../services/transaction.service.js';
import { sendSuccess, sendCreated, sendNoContent } from '../utils/response.js';
import type { CreateTransactionInput, UpdateTransactionInput, TransactionQuery } from '../types/transaction.types.js';

/**
 * Transaction Controller
 * Handles HTTP requests for transaction operations
 */
export class TransactionController {
  /**
   * Create a new transaction
   * POST /transactions
   */
  async create(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const data: CreateTransactionInput = req.body;

      const transaction = await transactionService.create(userId, data);
      sendCreated(res, transaction);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get all transactions
   * GET /transactions
   */
  async getAll(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const query: TransactionQuery = req.query as any;

      const result = await transactionService.getAll(userId, query);
      sendSuccess(res, result.transactions, 200, result.meta);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get transaction by ID
   * GET /transactions/:id
   */
  async getById(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;

      const transaction = await transactionService.getById(userId, id);
      sendSuccess(res, transaction);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Update transaction
   * PATCH /transactions/:id
   */
  async update(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;
      const data: UpdateTransactionInput = req.body;

      const transaction = await transactionService.update(userId, id, data);
      sendSuccess(res, transaction);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Delete transaction
   * DELETE /transactions/:id
   */
  async delete(req: AuthRequest, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = req.user!.id;
      const { id } = req.params;

      await transactionService.delete(userId, id);
      sendNoContent(res);
    } catch (error) {
      next(error);
    }
  }
}

export const transactionController = new TransactionController();
