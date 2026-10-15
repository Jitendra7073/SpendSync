import { describe, it, expect } from 'vitest';
import { decide, maskBody, samePayee, OTP, type Candidate, type Incoming } from './decide';
import { ingestSchema } from '../types/capture.types';

const t0 = new Date('2026-10-15T10:00:00Z');
const min = (n: number) => new Date(t0.getTime() + n * 60_000);
const auto = { mode: 'auto', duplicates: 'ask' } as const;

const sms: Incoming = { source: 'sms', amount: 500, type: 'debit', at: t0, refNo: '628112345678', last4: '1234', payee: 'Zomato' };
const captured = (over: Partial<Candidate> = {}): Candidate => ({
  transactionId: 'tx1', at: t0, messageId: 'm1', source: 'sms', refNo: null, last4: '1234', payee: 'ZOMATO LTD', ...over,
});

describe('decide', () => {
  it('same reference number → duplicate, whatever else differs', () => {
    expect(decide(sms, { messageId: 'm1', transactionId: 'tx1' }, [], auto)).toEqual({ status: 'duplicate', transactionId: 'tx1', duplicateOf: 'm1' });
  });

  it('bank email 5 hours after the SMS, same account → duplicate', () => {
    const email = { ...sms, source: 'email' as const, at: min(300), refNo: null };
    expect(decide(email, null, [captured()], auto).status).toBe('duplicate');
  });

  it('two SMS 2 hours apart are two payments', () => {
    expect(decide({ ...sms, at: min(120), refNo: null }, null, [captured()], auto).status).toBe('added');
  });

  it('different reference numbers are never merged', () => {
    expect(decide(sms, null, [captured({ refNo: '999999999999' })], auto).status).toBe('added');
  });

  it('different account last 4 is a different payment', () => {
    expect(decide({ ...sms, refNo: null }, null, [captured({ last4: '9876' })], auto).status).toBe('added');
  });

  it('a manual entry with the same amount is only ever a question', () => {
    const d = decide({ ...sms, refNo: null }, null, [captured({ messageId: null, source: null, last4: null, payee: null })], auto);
    expect(d).toMatchObject({ status: 'review', reason: 'possible_duplicate', transactionId: 'tx1' });
  });

  it('a weak match is kept when the user chose "keep both"', () => {
    const d = decide({ ...sms, refNo: null, last4: null, payee: null }, null, [captured()], { mode: 'auto', duplicates: 'keep' });
    expect(d.status).toBe('added');
  });

  it('several same-amount payments → review the closest', () => {
    const d = decide({ ...sms, refNo: null }, null, [captured({ transactionId: 'far', at: min(-20) }), captured({ transactionId: 'near', at: min(-5) })], auto);
    expect(d).toMatchObject({ status: 'review', transactionId: 'near' });
  });

  it('"Ask me first" turns a new payment into a question', () => {
    expect(decide(sms, null, [], { mode: 'ask', duplicates: 'ask' })).toMatchObject({ status: 'review', reason: 'ask_first' });
  });
});

describe('helpers', () => {
  it('masks account numbers to the last 4, keeps amounts', () => {
    expect(maskBody('A/c 50100123456789 debited Rs 1,250.00')).toBe('A/c ••••••••••6789 debited Rs 1,250.00');
  });
  it('payee match ignores case, punctuation and the UPI handle', () => {
    expect(samePayee('zomato@icici', 'ZOMATO LTD')).toBe(true);
    expect(samePayee('ab', 'ab')).toBe(false);
    expect(samePayee('Swiggy', 'Zomato')).toBe(false);
  });
  it('spots one-time codes', () => {
    expect(OTP.test('Your OTP for txn is 123456')).toBe(true);
    expect(OTP.test('Rs 500 debited from A/c XX1234')).toBe(false);
  });
});

describe('ingestSchema', () => {
  const base = { id: '123e4567-e89b-12d3-a456-426614174000', source: 'sms', sender: 'VM-HDFCBK', body: 'x', receivedAt: t0.toISOString() };
  it('a payment needs amount and type; an ignored message does not', () => {
    expect(() => ingestSchema.parse(base)).toThrow();
    expect(ingestSchema.parse({ ...base, ignoreReason: 'promo' }).ignoreReason).toBe('promo');
    expect(ingestSchema.parse({ ...base, amount: 10, type: 'debit' }).amount).toBe(10);
  });
  it('caps the body at 4000 characters', () => {
    expect(() => ingestSchema.parse({ ...base, ignoreReason: 'promo', body: 'x'.repeat(4001) })).toThrow();
  });
});
