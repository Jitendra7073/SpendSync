import { z } from 'zod';
import { billAiPrefsSchema } from '../billai/schema';
import { MAX_AI_IMAGE_BYTES, sniffMime } from '../billai/pages';

export const runReadSchema = z.object({ aiConsent: z.boolean(), force: z.boolean().default(false), prefs: billAiPrefsSchema });

const image = z
  .object({ mime: z.enum(['image/jpeg', 'image/png', 'image/webp']), base64: z.string().min(8).max(Math.ceil(MAX_AI_IMAGE_BYTES / 3) * 4 + 4) })
  .refine((i) => Buffer.byteLength(i.base64, 'base64') <= MAX_AI_IMAGE_BYTES, { message: 'Image too large' })
  .refine((i) => sniffMime(i.base64) === i.mime, { message: 'Not a real image of that type' });

export const readImagesSchema = z.object({ aiConsent: z.literal(true), images: z.array(image).min(1).max(3), prefs: billAiPrefsSchema });
