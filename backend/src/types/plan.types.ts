import { z } from 'zod';

export const monthSchema = z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/, 'Month must be YYYY-MM');
const daySchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Date must be YYYY-MM-DD');
const money = z.coerce.number().finite().min(0).max(1_000_000_000);

export const todayQuerySchema = z.object({ today: daySchema.optional() });

export const planItemSchema = z.object({
  category: z.string().trim().min(1).max(100),
  name: z.string().trim().min(1).max(100).optional(),
  kind: z.enum(['fixed', 'spend', 'savings']).default('spend'),
  limitAmount: money,
  sortOrder: z.coerce.number().int().min(0).max(200).default(0),
  rollover: z.boolean().default(false),
});

/** The whole month's plan in one go: the income and every bucket. Buckets not listed are removed. */
export const savePlanSchema = z
  .object({
    income: money,
    carryOver: money.default(0),
    items: z.array(planItemSchema).max(40),
  })
  .refine((p) => new Set(p.items.map((i) => i.category.toLowerCase())).size === p.items.length, {
    message: 'Each category can only have one bucket',
    path: ['items'],
  });

export const moveSchema = z
  .object({
    fromCategory: z.string().trim().min(1).max(100),
    toCategory: z.string().trim().min(1).max(100),
    amount: z.coerce.number().finite().positive().max(1_000_000_000),
  })
  .refine((m) => m.fromCategory !== m.toCategory, { message: 'Pick two different buckets', path: ['toCategory'] });

export type SavePlanInput = z.infer<typeof savePlanSchema>;
export type MoveInput = z.infer<typeof moveSchema>;

/** One turn of the AI planning guide: what was asked and answered so far, and which language to write in. */
export const guideSchema = z.object({
  month: monthSchema,
  income: money,
  language: z.enum(['English', 'Hindi', 'Spanish', 'French', 'German']).default('English'),
  /** Set when the user switches language on a question: ask about the same topic again, in the new language. */
  focus: z.string().max(60).optional(),
  transcript: z
    .array(z.object({ topic: z.string().max(60), question: z.string().max(240), answer: z.string().max(100) }))
    .max(10)
    .default([]),
});
