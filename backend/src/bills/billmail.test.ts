import { describe, expect, it, vi } from 'vitest';

// billsearch.ts also holds the DB query; these tests only use its pure helpers.
vi.stubEnv('DATABASE_URL', 'postgres://user:pass@localhost:5432/test');
vi.stubEnv('AUTH_SECRET', 'x'.repeat(40));
vi.mock('../db/index', () => ({ db: {} }));
import { emailBillsSchema, findBillsSchema, sendBillsSchema } from '../types/billmail.types';
import { amountWindow, fenceBill, rankMatches, type MatchRow } from '../assistant/billsearch';
import { composeBillsEmail, emailCooldown, maskEmail, packAttachments, type MailFile } from './billmail';

const MB = 1024 * 1024;
const row = (id: string, amount: number, merchant: string, day: string, read: unknown = null): MatchRow =>
  ({ id, date: new Date(`${day}T00:00:00Z`), amount, merchant, category: 'Shopping', note: null, billPages: 1, read });

describe('search helpers', () => {
  it('amount window is ±2% or ±₹20, whichever is wider', () => {
    expect(amountWindow(2000)).toEqual({ lo: 1960, hi: 2040 });
    expect(amountWindow(100)).toEqual({ lo: 80, hi: 120 });
  });
  it('ranks by closeness to the amount, then text match, then newest', () => {
    const rows = [row('far', 2040, 'Mall', '2026-10-10'), row('exact', 2000, 'Zara', '2026-10-01'), row('newer', 2000, 'Shop', '2026-10-09')];
    expect(rankMatches(rows, { amount: 2000 }).map((r) => r.id)).toEqual(['newer', 'exact', 'far']);
    expect(rankMatches(rows, { amount: 2000, query: 'zara' }).map((r) => r.id)).toEqual(['exact', 'newer', 'far']);
    expect(rankMatches(rows, {}).map((r) => r.id)).toEqual(['far', 'newer', 'exact']);
  });
  it('bill text for the model is capped and never more than 5 items', () => {
    const read = { merchant: 'Z'.repeat(200), total: 2150, items: Array.from({ length: 9 }, (_, i) => ({ name: `item ${i} ${'x'.repeat(100)}` })) };
    const f = fenceBill(read)!;
    expect(f.merchant).toHaveLength(60);
    expect(f.items).toHaveLength(5);
    expect(f.items[0].length).toBeLessThanOrEqual(60);
    expect(f.total).toBe(2150);
    expect(fenceBill(null)).toBeNull();
    expect(fenceBill({ total: 'x' })).toEqual({ merchant: null, total: null, items: [] });
  });
});

describe('email helpers', () => {
  const file = (n: string, size: number | null): MailFile => ({ filename: n, contentType: 'image/jpeg', bytes: size === null ? null : Buffer.alloc(size), label: `label ${n}` });
  it('packs files in order until 15 MB; failed fetches are listed too', () => {
    const r = packAttachments([file('a', 6 * MB), file('b', null), file('c', 6 * MB), file('d', 6 * MB)]);
    expect(r.attached.map((f) => f.filename)).toEqual(['a', 'c']);
    expect(r.notAttached).toEqual(['label b', 'label d']);
  });
  it('masks the address like the export email', () => expect(maskEmail('jitendra@gmail.com')).toBe('j***@gmail.com'));
  it('composes a plain list with full amounts and notes for missing files', () => {
    const m = composeBillsEmail(
      [{ date: '2026-10-09', merchant: 'Zara <b>', amount: 2150, summary: 'Clothes.', files: 2 }],
      ['label x'], 1,
    );
    expect(m.subject).toBe('Your bills from SpendSync (1)');
    expect(m.text).toContain('2026-10-09 · Zara <b> · ₹2,150.00');
    expect(m.text).toContain('Not attached');
    expect(m.text).toContain('1 bill was no longer available');
    expect(m.html).toContain('Zara &lt;b&gt;');
    expect(m.html).not.toContain('<b>');
  });
  it('cooldown: once a minute, 20 a day', () => {
    const now = new Date('2026-10-11T10:00:00Z');
    expect(emailCooldown(undefined, now)).toEqual({ ok: true });
    expect(emailCooldown({ count: 3, lastAt: new Date(now.getTime() - 20_000) }, now)).toEqual({ ok: false, reason: 'minute', waitSec: 40 });
    expect(emailCooldown({ count: 20, lastAt: new Date(now.getTime() - 3_600_000) }, now)).toMatchObject({ ok: false, reason: 'day' });
    expect(emailCooldown({ count: 19, lastAt: new Date(now.getTime() - 61_000) }, now)).toEqual({ ok: true });
  });
});


describe('inputs', () => {
  const id = '00000000-0000-4000-8000-000000000001';
  it('email_bills takes only transaction ids, 1–10, and no recipient', () => {
    expect(emailBillsSchema.safeParse({ transaction_ids: [id] }).success).toBe(true);
    expect(emailBillsSchema.safeParse({ transaction_ids: [] }).success).toBe(false);
    expect(emailBillsSchema.safeParse({ transaction_ids: Array(11).fill(id) }).success).toBe(false);
    expect(emailBillsSchema.parse({ transaction_ids: [id], to: 'x@evil.com' })).not.toHaveProperty('to');
    expect(sendBillsSchema.safeParse({ transactionIds: ['nope'] }).success).toBe(false);
  });
  it('find_bills bounds its filters', () => {
    expect(findBillsSchema.parse({})).toMatchObject({ has_bill: 'any', limit: 5 });
    expect(findBillsSchema.safeParse({ limit: 9 }).success).toBe(false);
    expect(findBillsSchema.safeParse({ start_date: '9 Oct' }).success).toBe(false);
  });
});
