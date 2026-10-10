import { describe, expect, it } from 'vitest';
import { assertInTrash, assertParentLive, decodeCursor, encodeCursor, mergePage } from './rules';

const id = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const row = (n: number, iso: string) => ({ id: id(n), deletedAt: new Date(iso) });

describe('cursor', () => {
  it('round-trips', () => {
    const c = { at: '2026-10-10T10:00:00.000Z', id: id(1) };
    expect(decodeCursor(encodeCursor(c))).toEqual(c);
  });
  it('is null when absent', () => {
    expect(decodeCursor(undefined)).toBeNull();
    expect(decodeCursor('')).toBeNull();
  });
  it('rejects forged or broken cursors with 400', () => {
    for (const bad of ['not-base64!!', Buffer.from('{"at":"x","id":"y"}').toString('base64url'), Buffer.from('[]').toString('base64url'), Buffer.from(`{"at":"2026-10-10T10:00:00.000Z","id":"1; drop table"}`).toString('base64url')]) {
      expect(() => decodeCursor(bad)).toThrow(expect.objectContaining({ statusCode: 400 }));
    }
  });
});

describe('mergePage', () => {
  it('merges two newest-first lists into one page and points the cursor at the last item', () => {
    const txs = [row(5, '2026-10-10T10:00:00Z'), row(3, '2026-10-08T10:00:00Z'), row(1, '2026-10-01T10:00:00Z')];
    const holds = [row(4, '2026-10-09T10:00:00Z'), row(2, '2026-10-05T10:00:00Z')];
    const page = mergePage(txs, holds, 3);
    expect(page.items.map((r) => r.id)).toEqual([id(5), id(4), id(3)]);
    expect(decodeCursor(page.nextCursor!)).toEqual({ at: '2026-10-08T10:00:00.000Z', id: id(3) });
  });
  it('breaks ties on id descending, like the SQL ORDER BY', () => {
    const same = '2026-10-10T10:00:00Z';
    expect(mergePage([row(1, same)], [row(2, same)], 5).items.map((r) => r.id)).toEqual([id(2), id(1)]);
  });
  it('has no next cursor on the last page', () => {
    expect(mergePage([row(1, '2026-10-10T10:00:00Z')], [], 3).nextCursor).toBeNull();
  });
});

describe('guards', () => {
  it('hard delete only for rows already in the Trash', () => {
    expect(() => assertInTrash(undefined)).toThrow(expect.objectContaining({ statusCode: 404 }));
    expect(() => assertInTrash({ deletedAt: null })).toThrow(expect.objectContaining({ statusCode: 409, code: 'NOT_IN_TRASH' }));
    expect(() => assertInTrash({ deletedAt: new Date() })).not.toThrow();
  });
  it('a hold cannot come back while its transaction is in the Trash', () => {
    expect(() => assertParentLive(new Date())).toThrow(expect.objectContaining({ statusCode: 409, code: 'PARENT_DELETED' }));
    expect(() => assertParentLive(null)).not.toThrow();
  });
});
