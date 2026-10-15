import { createHash } from 'node:crypto';
import { v2 as cloudinary } from 'cloudinary';
import { configureCloudinary, type CloudinaryCreds } from '../bills/cloudinary';

export const MAX_AI_IMAGE_BYTES = 1_048_576;
export type PageRef = { id: string; publicId: string; version: number | null; format: string | null; pages: number | null };

/** Identity of "these exact pages": a new, replaced or deleted page changes it. */
export function pagesKey(bills: { id: string; version: number | null; pages: number | null }[]): string {
  const parts = bills.map((b) => `${b.id}:${b.version ?? 0}:${b.pages ?? 1}`).sort();
  return createHash('sha256').update(parts.join('|')).digest('hex').slice(0, 32);
}

/** Bills in position order; each image is one page, each PDF page is one page; at most [max]. */
export function pagePlan(bills: PageRef[], max = 5) {
  const out: { publicId: string; version: number | null; page: number | null }[] = [];
  for (const b of bills) {
    const n = b.format === 'pdf' ? Math.max(1, b.pages ?? 1) : 0;
    if (n === 0) out.push({ publicId: b.publicId, version: b.version, page: null });
    else for (let p = 1; p <= n && out.length < max; p++) out.push({ publicId: b.publicId, version: b.version, page: p });
    if (out.length >= max) break;
  }
  return out.slice(0, max);
}

/** Signed, small JPEG of one page — what the model sees (≈150–300 KB). */
export function readImageUrl(c: CloudinaryCreds, publicId: string, version: number | null, page: number | null): string {
  configureCloudinary(c);
  return cloudinary.url(publicId, {
    type: 'authenticated', sign_url: true, secure: true, resource_type: 'image', format: 'jpg',
    ...(version ? { version } : {}),
    transformation: [{ crop: 'limit', width: 1600, height: 1600, ...(page ? { page } : {}) }, { quality: 70 }],
  });
}

/** Real type from the first bytes (never trust a declared MIME). */
export function sniffMime(base64: string): 'image/jpeg' | 'image/png' | 'image/webp' | null {
  const b = Buffer.from(base64.slice(0, 32), 'base64');
  if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'image/jpeg';
  if (b[0] === 0x89 && b.toString('latin1', 1, 4) === 'PNG') return 'image/png';
  if (b.toString('latin1', 0, 4) === 'RIFF' && b.toString('latin1', 8, 12) === 'WEBP') return 'image/webp';
  return null;
}
