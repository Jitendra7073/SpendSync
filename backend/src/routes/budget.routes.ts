import { Router } from 'express';
import { budgetController } from '../controllers/budget.controller.js';
import { authenticate } from '../middleware/auth.middleware.js';
import { validate } from '../middleware/validate.middleware.js';
import {
  createBudgetSchema,
  updateBudgetSchema,
  budgetQuerySchema,
  budgetIdSchema,
} from '../types/budget.types.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

/**
 * @route   POST /api/budgets
 * @desc    Create a new budget
 * @access  Private
 */
router.post('/', validate(createBudgetSchema), budgetController.create.bind(budgetController));

/**
 * @route   GET /api/budgets
 * @desc    Get all budgets with filters
 * @access  Private
 */
router.get('/', validate(budgetQuerySchema, 'query'), budgetController.getAll.bind(budgetController));

/**
 * @route   GET /api/budgets/:id
 * @desc    Get budget by ID
 * @access  Private
 */
router.get('/:id', validate(budgetIdSchema, 'params'), budgetController.getById.bind(budgetController));

/**
 * @route   PATCH /api/budgets/:id
 * @desc    Update budget
 * @access  Private
 */
router.patch(
  '/:id',
  validate(budgetIdSchema, 'params'),
  validate(updateBudgetSchema),
  budgetController.update.bind(budgetController)
);

/**
 * @route   DELETE /api/budgets/:id
 * @desc    Delete budget
 * @access  Private
 */
router.delete('/:id', validate(budgetIdSchema, 'params'), budgetController.delete.bind(budgetController));

export default router;
