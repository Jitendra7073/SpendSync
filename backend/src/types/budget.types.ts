import { z } from 'zod';

/**
 * Budget creation schema
 */
export const createBudgetSchema = z.object({
  category: z.string().min(1).max(100),
  month: z.string().regex(/^\d{4}-\d{2}$/, 'Month must be in YYYY-MM format'),
  limitAmount: z.string().or(z.number()).transform((val) => {
    const num = typeof val === 'string' ? parseFloat(val) : val;
    if (isNaN(num) || num <= 0) {
      throw new Error('Limit amount must be a positive number');
    }
    return num.toFixed(2);
  }),
});

/**
 * Budget update schema
 */
export const updateBudgetSchema = createBudgetSchema.partial();

/**
 * Budget query schema
 */
export const budgetQuerySchema = z.object({
  month: z.string().regex(/^\d{4}-\d{2}$/).optional(),
  category: z.string().optional(),
});

/**
 * Budget ID param schema
 */
export const budgetIdSchema = z.object({
  id: z.string().uuid(),
});

// Type exports
export type CreateBudgetInput = z.infer<typeof createBudgetSchema>;
export type UpdateBudgetInput = z.infer<typeof updateBudgetSchema>;
export type BudgetQuery = z.infer<typeof budgetQuerySchema>;
