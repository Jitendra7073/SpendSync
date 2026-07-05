import { z } from 'zod';
import { transactionTypes } from '../db/schema/transactions.schema.js';

/**
 * Transaction creation schema
 */
export const createTransactionSchema = z.object({
  amount: z.string().or(z.number()).transform((val) => {
    const num = typeof val === 'string' ? parseFloat(val) : val;
    if (isNaN(num) || num <= 0) {
      throw new Error('Amount must be a positive number');
    }
    return num.toFixed(2);
  }),
  type: z.enum(transactionTypes),
  merchant: z.string().min(1).max(200),
  category: z.string().min(1).max(100),
  sourceApp: z.string().max(50).optional(),
  note: z.string().max(500).optional(),
});

/**
 * Transaction update schema
 */
export const updateTransactionSchema = createTransactionSchema.partial();

/**
 * Transaction query schema
 */
export const transactionQuerySchema = z.object({
  startDate: z.string().datetime().optional(),
  endDate: z.string().datetime().optional(),
  category: z.string().optional(),
  type: z.enum(transactionTypes).optional(),
  page: z.string().transform(Number).default('1'),
  limit: z.string().transform(Number).default('50'),
});

/**
 * Transaction ID param schema
 */
export const transactionIdSchema = z.object({
  id: z.string().uuid(),
});

// Type exports
export type CreateTransactionInput = z.infer<typeof createTransactionSchema>;
export type UpdateTransactionInput = z.infer<typeof updateTransactionSchema>;
export type TransactionQuery = z.infer<typeof transactionQuerySchema>;
