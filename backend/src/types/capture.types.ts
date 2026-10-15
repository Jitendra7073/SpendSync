import { z } from 'zod';

export const CAPTURE_SOURCES = ['sms', 'email', 'upi'] as const;
export const CAPTURE_STATUSES = ['added', 'duplicate', 'review', 'ignored'] as const;
export const IGNORE_REASONS = ['promo', 'failed', 'future', 'request', 'balance', 'not_payment'] as const;

const money = z.number().positive().max(100_000_000);

/** One message the phone saw. Parsed fields are absent when the phone decided to ignore it. */
export const ingestSchema = z
  .object({
    id: z.string().uuid(),
    source: z.enum(CAPTURE_SOURCES),
    sender: z.string().trim().min(1).max(120),
    packageName: z.string().max(200).optional(),
    body: z.string().min(1).max(4000),
    receivedAt: z.string().datetime(),
    amount: money.optional(),
    type: z.enum(['debit', 'credit']).optional(),
    payee: z.string().trim().max(200).optional(),
    refNo: z.string().trim().max(64).optional(),
    last4: z.string().regex(/^\d{3,4}$/).optional(),
    balance: z.number().max(10_000_000_000).optional(),
    ignoreReason: z.enum(IGNORE_REASONS).optional(),
  })
  .refine((m) => m.ignoreReason || (m.amount && m.type), { message: 'A payment needs amount and type' });

export const listQuerySchema = z.object({
  status: z.enum(CAPTURE_STATUSES).optional(),
  transactionId: z.string().uuid().optional(),
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

/** add = make it a transaction; same / different = answer a duplicate question; ignore = dismiss. */
export const actionSchema = z.object({ action: z.enum(['add', 'same', 'different', 'ignore']) });

export const messageIdSchema = z.string().uuid();

export type IngestInput = z.infer<typeof ingestSchema>;
export type ListQuery = z.infer<typeof listQuerySchema>;
export type CaptureAction = z.infer<typeof actionSchema>['action'];
