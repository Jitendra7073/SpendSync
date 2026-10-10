import { describe, expect, it } from 'vitest';
import { confirmBillSchema, reserveBillSchema } from '../types/bill.types';
import { checkDaily, checkQuota, confirmDecision, firstFreePosition, newPublicId, purgeBackoffMs, restorePosition, userPrefix, utcDay } from './rules';

const MB = 1024 * 1024;
const q = { livePages: 0, replacing: false, bytesUsed: 0, bytes: MB, maxBytes: 500 * MB };

describe('quota', () => {
  it('allows a normal upload', () => expect(() => checkQuota(q)).not.toThrow());
  it('caps a transaction at 5 pages, except when replacing one', () => {
    expect(() => checkQuota({ ...q, livePages: 5 })).toThrow(expect.objectContaining({ statusCode: 409, code: 'BILL_LIMIT' }));
    expect(() => checkQuota({ ...q, livePages: 5, replacing: true })).not.toThrow();
  });
  it('refuses when storage would overflow', () => {
    expect(() => checkQuota({ ...q, bytesUsed: 500 * MB - 10, bytes: 11 })).toThrow(expect.objectContaining({ statusCode: 409, code: 'STORAGE_FULL' }));
    expect(() => checkQuota({ ...q, bytesUsed: 500 * MB - 10, bytes: 10 })).not.toThrow();
  });
  it('refuses past the daily cap', () => {
    expect(() => checkDaily(50, 50)).not.toThrow();
    expect(() => checkDaily(51, 50)).toThrow(expect.objectContaining({ statusCode: 429, code: 'DAILY_LIMIT' }));
  });
});

describe('positions', () => {
  it('first free slot', () => {
    expect(firstFreePosition([])).toBe(0);
    expect(firstFreePosition([0, 1, 3])).toBe(2);
    expect(firstFreePosition([0, 1, 2, 3, 4])).toBeNull();
  });
  it('restore goes back to its slot, else the first free one, else refuses', () => {
    expect(restorePosition([0, 2], 1)).toBe(1);
    expect(restorePosition([0, 1], 1)).toBe(2);
    expect(() => restorePosition([0, 1, 2, 3, 4], 1)).toThrow(expect.objectContaining({ code: 'BILL_LIMIT' }));
  });
});

describe('purge backoff', () => {
  it('doubles from one minute and caps at a day', () => {
    expect(purgeBackoffMs(1)).toBe(2 * 60_000);
    expect(purgeBackoffMs(3)).toBe(8 * 60_000);
    expect(purgeBackoffMs(30)).toBe(24 * 3_600_000);
  });
});

describe('confirm', () => {
  const pending = { status: 'pending' as const, publicId: 'p' };
  it('decides each case', () => {
    expect(confirmDecision(undefined, 'p', 1)).toBe('unknown');
    expect(confirmDecision(pending, 'other', 1)).toBe('mismatch');
    expect(confirmDecision({ ...pending, status: 'ready' }, 'p', 1)).toBe('replay');
    expect(confirmDecision(pending, 'p', 10 * MB + 1)).toBe('too_large');
    expect(confirmDecision(pending, 'p', 10 * MB)).toBe('ready');
  });
});

describe('ids', () => {
  it('nest every file under the user folder', () => {
    expect(newPublicId('u_1', 'abc')).toBe('spendsync/bills/u_1/abc');
    expect(newPublicId('u_1', 'abc').startsWith(userPrefix('u_1'))).toBe(true);
    expect(utcDay(new Date('2026-10-10T23:30:00-05:00'))).toBe('2026-10-11');
  });
});

describe('bill input', () => {
  const key = '00000000-0000-4000-8000-000000000001';
  it('reserve accepts the allowed types up to 10 MB', () => {
    expect(reserveBillSchema.safeParse({ clientKey: key, contentType: 'application/pdf', bytes: 10 * MB }).success).toBe(true);
    expect(reserveBillSchema.safeParse({ clientKey: key, contentType: 'image/gif', bytes: 1 }).success).toBe(false);
    expect(reserveBillSchema.safeParse({ clientKey: key, contentType: 'image/jpeg', bytes: 10 * MB + 1 }).success).toBe(false);
    expect(reserveBillSchema.safeParse({ clientKey: key, contentType: 'image/jpeg', bytes: 1, position: 5 }).success).toBe(false);
  });
  it("confirm needs Cloudinary's signature fields", () => {
    expect(confirmBillSchema.safeParse({ public_id: 'p', version: 1, signature: 'abcdefabcdef', format: 'jpg', bytes: 5 }).success).toBe(true);
    expect(confirmBillSchema.safeParse({ public_id: 'p', version: 1, format: 'jpg', bytes: 5 }).success).toBe(false);
  });
});
