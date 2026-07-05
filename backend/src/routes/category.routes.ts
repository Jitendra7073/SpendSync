import { Router } from 'express';
import { categoryController } from '../controllers/category.controller.js';
import { authenticate } from '../middleware/auth.middleware.js';
import { validate } from '../middleware/validate.middleware.js';
import {
  createCategorySchema,
  updateCategorySchema,
  categoryIdSchema,
} from '../types/category.types.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

/**
 * @route   POST /api/categories
 * @desc    Create a new category rule
 * @access  Private
 */
router.post('/', validate(createCategorySchema), categoryController.create.bind(categoryController));

/**
 * @route   POST /api/categories/suggest
 * @desc    Get category suggestion for a merchant
 * @access  Private
 */
router.post('/suggest', categoryController.suggest.bind(categoryController));

/**
 * @route   GET /api/categories
 * @desc    Get all category rules
 * @access  Private
 */
router.get('/', categoryController.getAll.bind(categoryController));

/**
 * @route   GET /api/categories/:id
 * @desc    Get category by ID
 * @access  Private
 */
router.get('/:id', validate(categoryIdSchema, 'params'), categoryController.getById.bind(categoryController));

/**
 * @route   PATCH /api/categories/:id
 * @desc    Update category rule
 * @access  Private
 */
router.patch(
  '/:id',
  validate(categoryIdSchema, 'params'),
  validate(updateCategorySchema),
  categoryController.update.bind(categoryController)
);

/**
 * @route   DELETE /api/categories/:id
 * @desc    Delete category rule
 * @access  Private
 */
router.delete('/:id', validate(categoryIdSchema, 'params'), categoryController.delete.bind(categoryController));

export default router;
