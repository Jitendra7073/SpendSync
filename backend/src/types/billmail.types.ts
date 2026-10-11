import { z } from 'zod';

const day = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const ids = z.array(z.string().uuid()).min(1).max(10);

export const findBillsSchema = z.object({
  query: z.string().trim().min(1).max(80).optional(),
  amount: z.number().positive().max(10_000_000).optional(),
  start_date: day.optional(),
  end_date: day.optional(),
  category: z.string().trim().min(1).max(40).optional(),
  has_bill: z.enum(['any', 'with', 'without']).default('any'),
  limit: z.number().int().min(1).max(5).default(5),
});
export type FindBillsInput = z.infer<typeof findBillsSchema>;

/** No recipient on purpose: the server only ever emails the account address. */
export const emailBillsSchema = z.object({ transaction_ids: ids });
export const sendBillsSchema = z.object({ transactionIds: ids });
