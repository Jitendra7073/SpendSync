import { z } from 'zod';

/**
 * Category creation schema
 */
export const createCategorySchema = z.object({
  keyword: z.string().min(1).max(100).toLowerCase(),
  category: z.string().min(1).max(100),
});

/**
 * Category update schema
 */
export const updateCategorySchema = createCategorySchema.partial();

/**
 * Category ID param schema
 */
export const categoryIdSchema = z.object({
  id: z.string().uuid(),
});

// Type exports
export type CreateCategoryInput = z.infer<typeof createCategorySchema>;
export type UpdateCategoryInput = z.infer<typeof updateCategorySchema>;
