import { Router } from 'express';
import { transactionController } from '../controllers/transaction.controller.js';
import { authenticate } from '../middleware/auth.middleware.js';
import { validate } from '../middleware/validate.middleware.js';
import {
  createTransactionSchema,
  updateTransactionSchema,
  transactionQuerySchema,
  transactionIdSchema,
} from '../types/transaction.types.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

/**
 * @route   POST /api/transactions
 * @desc    Create a new transaction
 * @access  Private
 */
router.post('/', validate(createTransactionSchema), transactionController.create.bind(transactionController));

/**
 * @route   GET /api/transactions
 * @desc    Get all transactions with filters
 * @access  Private
 */
router.get('/', validate(transactionQuerySchema, 'query'), transactionController.getAll.bind(transactionController));

/**
 * @route   GET /api/transactions/:id
 * @desc    Get transaction by ID
 * @access  Private
 */
router.get('/:id', validate(transactionIdSchema, 'params'), transactionController.getById.bind(transactionController));

/**
 * @route   PATCH /api/transactions/:id
 * @desc    Update transaction
 * @access  Private
 */
router.patch(
  '/:id',
  validate(transactionIdSchema, 'params'),
  validate(updateTransactionSchema),
  transactionController.update.bind(transactionController)
);

/**
 * @route   DELETE /api/transactions/:id
 * @desc    Delete transaction
 * @access  Private
 */
router.delete('/:id', validate(transactionIdSchema, 'params'), transactionController.delete.bind(transactionController));

export default router;
