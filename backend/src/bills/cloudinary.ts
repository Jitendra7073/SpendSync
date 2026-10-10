import { createHash, timingSafeEqual } from 'node:crypto';
import { v2 as cloudinary } from 'cloudinary';

export interface CloudinaryCreds {
  cloudName: string;
  apiKey: string;
  apiSecret: string;
}
export interface BillUrls {
  thumb: string;
  full: string;
  blurred: string;
  pageUrls: string[];
  original: string | null;
}

export const ALLOWED_FORMATS = 'jpg,png,webp,heic,pdf';
export const MAX_BILL_BYTES = 10 * 1024 * 1024;
const MAX_PDF_PAGES_SHOWN = 20;

let configuredFor = '';
/** The SDK keeps one global config; set it once per credentials. */
export function configureCloudinary(c: CloudinaryCreds) {
  const key = `${c.cloudName}:${c.apiKey}`;
  if (configuredFor === key) return;
  // timeout: a hanging Cloudinary must not hold a user request; failed purges are retried by the cron.
  cloudinary.config({ cloud_name: c.cloudName, api_key: c.apiKey, api_secret: c.apiSecret, secure: true, timeout: 10_000 });
  configuredFor = key;
}

function same(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

/** Signed params for ONE upload into `publicId`. Strings only (the phone sends them back verbatim). */
export function uploadParams(c: CloudinaryCreds, publicId: string, notificationUrl: string, timestampSec: number) {
  const signed: Record<string, string> = {
    public_id: publicId,
    timestamp: String(timestampSec),
    type: 'authenticated',
    overwrite: 'false',
    allowed_formats: ALLOWED_FORMATS,
    notification_url: notificationUrl,
  };
  const signature = cloudinary.utils.api_sign_request(signed, c.apiSecret);
  return {
    uploadUrl: `https://api.cloudinary.com/v1_1/${c.cloudName}/image/upload`,
    params: { ...signed, api_key: c.apiKey, signature },
  };
}

/** Cloudinary signs (public_id, version) in its upload response with our secret. */
export function verifyUploadResponse(c: CloudinaryCreds, r: { public_id: string; version: number; signature: string }): boolean {
  const expected = cloudinary.utils.api_sign_request({ public_id: r.public_id, version: r.version }, c.apiSecret);
  return same(expected, r.signature);
}

/** X-Cld-Signature = sha1(raw body + X-Cld-Timestamp + secret); valid for 2 hours. */
export function verifyWebhook(c: CloudinaryCreds, rawBody: string, timestamp: string | null, signature: string | null, nowSec: number): boolean {
  if (!timestamp || !signature) return false;
  const ts = Number(timestamp);
  if (!Number.isFinite(ts) || nowSec - ts > 7200 || ts - nowSec > 300) return false;
  const expected = createHash('sha1').update(rawBody + timestamp + c.apiSecret).digest('hex');
  return same(expected, signature);
}

export function deliveryUrls(c: CloudinaryCreds, b: { publicId: string; version: number | null; format: string | null; pages: number | null }): BillUrls {
  configureCloudinary(c);
  const isPdf = b.format === 'pdf';
  const base = { type: 'authenticated', sign_url: true, secure: true, resource_type: 'image', ...(b.version ? { version: b.version } : {}) };
  const asImage = isPdf ? { format: 'jpg' } : {};
  const url = (first: Record<string, unknown>) =>
    cloudinary.url(b.publicId, { ...base, ...asImage, transformation: [first, { fetch_format: 'auto', quality: 'auto' }] });
  const page = (n: number) => (isPdf ? { page: n } : {});

  const full = url({ crop: 'limit', width: 2000, height: 2000, ...page(1) });
  const count = isPdf ? Math.min(Math.max(b.pages ?? 1, 1), MAX_PDF_PAGES_SHOWN) : 1;
  return {
    thumb: url({ crop: 'fill', width: 300, height: 300, ...page(1) }),
    full,
    blurred: url({ crop: 'fill', width: 300, height: 300, effect: 'blur:2000', ...page(1) }),
    pageUrls: isPdf ? Array.from({ length: count }, (_, i) => url({ crop: 'limit', width: 2000, height: 2000, page: i + 1 })) : [full],
    original: isPdf ? cloudinary.url(b.publicId, { ...base, format: 'pdf' }) : null,
  };
}

/** 'not found' counts as done: the goal is that the file is gone. */
export async function destroyAsset(c: CloudinaryCreds, publicId: string): Promise<void> {
  configureCloudinary(c);
  const r = await cloudinary.uploader.destroy(publicId, { type: 'authenticated', resource_type: 'image', invalidate: true });
  if (r?.result !== 'ok' && r?.result !== 'not found') throw new Error(`destroy ${publicId}: ${r?.result ?? 'no result'}`);
}

export async function destroyPrefix(c: CloudinaryCreds, prefix: string): Promise<{ partial: boolean }> {
  configureCloudinary(c);
  const r = await cloudinary.api.delete_resources_by_prefix(prefix, { type: 'authenticated', resource_type: 'image', invalidate: true });
  return { partial: Boolean(r?.partial) };
}

export async function listAssets(c: CloudinaryCreds, prefix: string, cursor?: string): Promise<{ items: { publicId: string; bytes: number }[]; next?: string }> {
  configureCloudinary(c);
  const r = await cloudinary.api.resources({ type: 'authenticated', resource_type: 'image', prefix, max_results: 500, ...(cursor ? { next_cursor: cursor } : {}) });
  return {
    items: (r.resources ?? []).map((x: { public_id: string; bytes: number }) => ({ publicId: x.public_id, bytes: x.bytes })),
    next: r.next_cursor as string | undefined,
  };
}
