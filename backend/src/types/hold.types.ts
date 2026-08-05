import { z } from 'zod';
import { holdDirections, holdStatuses } from '../db/schema/holds.schema';

export const createHoldSchema = z.object({
  transactionId: z.string().uuid(),
  direction: z.enum(holdDirections),
  personName: z.string().min(1).max(200),
  amount: z
    .string()
    .or(z.number())
    .transform((val) => {
      const num = typeof val === 'string' ? parseFloat(val) : val;
      if (isNaN(num) || num <= 0) {
        throw new Error('Amount must be a positive number');
      }
      return num.toFixed(2);
    }),
  expectedReturnDate: z.string().datetime(),
});

export const updateHoldSchema = z.object({
  personName: z.string().min(1).max(200).optional(),
  expectedReturnDate: z.string().datetime().optional(),
  status: z.enum(holdStatuses).optional(),
});

export const holdQuerySchema = z.object({
  status: z.enum(holdStatuses).optional(),
  direction: z.enum(holdDirections).optional(),
});

export const holdIdSchema = z.object({
  id: z.string().uuid(),
});

export type CreateHoldInput = z.infer<typeof createHoldSchema>;
export type UpdateHoldInput = z.infer<typeof updateHoldSchema>;
export type HoldQuery = z.infer<typeof holdQuerySchema>;
