import { z } from 'zod';

export const trashQuerySchema = z.object({
  cursor: z.string().max(300).optional(),
  limit: z.coerce.number().int().min(1).max(50).default(30),
});

export const trashTargetSchema = z.object({
  kind: z.enum(['transaction', 'hold']),
  id: z.string().uuid(),
});
export type TrashKind = z.infer<typeof trashTargetSchema>['kind'];
