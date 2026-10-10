import { z } from 'zod';
import { MAX_BILL_BYTES } from '../bills/cloudinary';

export const billIdSchema = z.object({ id: z.string().uuid() });

export const reserveBillSchema = z.object({
  clientKey: z.string().uuid(),
  contentType: z.enum(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'application/pdf']),
  bytes: z.number().int().positive().max(MAX_BILL_BYTES),
  position: z.number().int().min(0).max(4).optional(),
  replaces: z.string().uuid().optional(),
});
export type ReserveBillInput = z.infer<typeof reserveBillSchema>;

const uploadResult = {
  public_id: z.string().min(1).max(300),
  version: z.number().int().positive(),
  format: z.string().max(10),
  bytes: z.number().int().nonnegative(),
  width: z.number().int().nonnegative().optional(),
  height: z.number().int().nonnegative().optional(),
  pages: z.number().int().positive().optional(),
};
/** What the phone forwards from Cloudinary's upload response. */
export const confirmBillSchema = z.object({ ...uploadResult, signature: z.string().min(10).max(128) });
/** Cloudinary's upload webhook body (already verified by header signature). Unknown fields ignored. */
export const webhookUploadSchema = z.object(uploadResult);
export type UploadResult = z.infer<typeof webhookUploadSchema>;
