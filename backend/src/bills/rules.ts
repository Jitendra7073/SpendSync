import { ApiError, ConflictError } from '../utils/errors';
import { MAX_BILL_BYTES } from './cloudinary';

export const MAX_PAGES = 5;
export const BILLS_ROOT = 'spendsync/bills/';

export const userPrefix = (userId: string) => `${BILLS_ROOT}${userId}/`;
export const newPublicId = (userId: string, uuid: string) => `${userPrefix(userId)}${uuid}`;
export const utcDay = (now: Date) => now.toISOString().slice(0, 10);

export interface QuotaInput {
  livePages: number;
  replacing: boolean;
  bytesUsed: number;
  bytes: number;
  maxBytes: number;
}

export function checkQuota(q: QuotaInput): void {
  if (!q.replacing && q.livePages >= MAX_PAGES) throw new ConflictError('A transaction can have up to 5 bill pages', 'BILL_LIMIT');
  if (q.bytesUsed + q.bytes > q.maxBytes) throw new ConflictError('Storage full — empty Trash or delete old bills', 'STORAGE_FULL');
}

export function checkDaily(countAfterIncrement: number, max: number): void {
  if (countAfterIncrement > max) throw new ApiError(429, 'Daily upload limit reached — try again tomorrow', 'DAILY_LIMIT');
}

export function firstFreePosition(taken: number[]): number | null {
  for (let p = 0; p < MAX_PAGES; p++) if (!taken.includes(p)) return p;
  return null;
}

export function restorePosition(taken: number[], old: number): number {
  if (!taken.includes(old)) return old;
  const p = firstFreePosition(taken);
  if (p === null) throw new ConflictError('This transaction already has 5 bill pages', 'BILL_LIMIT');
  return p;
}

/** 2^attempts minutes, at most a day. */
export function purgeBackoffMs(attempts: number): number {
  return Math.min(2 ** attempts * 60_000, 24 * 3_600_000);
}

export function confirmDecision(row: { status: 'pending' | 'ready'; publicId: string } | undefined, publicId: string, bytes: number) {
  if (!row) return 'unknown' as const;
  if (row.publicId !== publicId) return 'mismatch' as const;
  if (row.status === 'ready') return 'replay' as const;
  if (bytes > MAX_BILL_BYTES) return 'too_large' as const;
  return 'ready' as const;
}
