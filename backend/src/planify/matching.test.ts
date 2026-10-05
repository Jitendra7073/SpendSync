import { describe, expect, it } from 'vitest';
import { allocate, findCandidates, normalize, similarity, type Feedback, type TxLite } from './matching';

const tx = (category: string, amount: number, merchant = '', type = 'debit'): TxLite => ({ category, merchant, amount, type });
const buckets = [{ category: 'Eating out', name: 'Eating out', kind: 'spend' }, { category: 'Transport', name: 'Transport', kind: 'spend' }, { category: 'Emergency fund', name: 'Emergency fund', kind: 'savings' }];

describe('similarity', () => {
  it('links related names but not unrelated or vague ones', () => {
    expect(similarity('Eating out', 'Food')).toBe(0.9);
    expect(similarity('Eating out', 'Restaurants')).toBe(0.9);
    expect(similarity('Eating out', 'Dining')).toBe(0.9);
    expect(similarity('Eating out', 'Rent')).toBe(0);
    expect(similarity('Eating out', 'Other')).toBe(0);
    expect(similarity('Food court', 'Food truck')).toBeGreaterThanOrEqual(0.6);
  });
  it('normalises', () => expect(normalize('  Eating-Out!  ')).toBe('eating out'));
});

describe('findCandidates', () => {
  const txs = [tx('Food', 300), tx('Food', 500), tx('Rent', 12000), tx('Other', 250, 'Zomato'), tx('Other', 90, 'Corner shop'), tx('Eating out', 100)];

  it('suggests related categories and merchants, never unrelated ones, and not what a bucket already counts', () => {
    const c = findCandidates(txs, buckets, []);
    const food = c.find((x) => x.kind === 'category')!;
    expect(food).toMatchObject({ bucket: 'Eating out', label: 'Food', count: 2, total: 800 });
    expect(c.some((x) => x.label === 'Rent')).toBe(false);
    expect(c.some((x) => x.label === 'Corner shop')).toBe(false);
    expect(findCandidates([tx('Other', 250, 'Zomato')], buckets, [])[0]).toMatchObject({ kind: 'merchant', label: 'Zomato', bucket: 'Eating out' });
  });

  it('never asks again once answered, and learns from a Yes', () => {
    const no: Feedback = { bucket: 'Eating out', kind: 'category', key: 'food', label: 'Food', verdict: 'no' };
    expect(findCandidates(txs, buckets, [no]).some((x) => x.label === 'Food')).toBe(false);
    const yes: Feedback = { bucket: 'Eating out', kind: 'category', key: 'food court', label: 'Food court', verdict: 'yes' };
    // "Food truck" shares a word with the confirmed "Food court", so it becomes a candidate even without a word-group hit
    expect(findCandidates([tx('Food truck', 200)], [{ category: 'Treats', name: 'Treats', kind: 'spend' }], [{ ...yes, bucket: 'Treats' }])[0]?.label).toBe('Food truck');
  });

  it('skips savings buckets', () => {
    expect(findCandidates([tx('Emergency', 100)], [buckets[2]], [])).toEqual([]);
  });
});

describe('allocate', () => {
  const aliases = [
    { bucket: 'Eating out', kind: 'category' as const, key: 'food', label: 'Food' },
    { bucket: 'Eating out', kind: 'merchant' as const, key: 'zomato', label: 'Zomato' },
  ];
  it('counts confirmed categories and merchants once, with own bucket first, and refunds reduce it', () => {
    const r = allocate([tx('Eating out', 100), tx('Food', 300), tx('Other', 250, 'Zomato'), tx('Food', 50, '', 'credit'), tx('Rent', 12000), tx('Other', 90, 'Corner shop')], buckets, aliases);
    expect(r['Eating out']).toBe(600); // 100 + 300 + 250 - 50
    expect(r['Rent']).toBe(12000); // no bucket, no alias: stays on its own
    expect(r['Other']).toBe(90);
  });
  it('without aliases nothing changes', () => {
    expect(allocate([tx('Food', 300)], buckets, [])).toEqual({ Food: 300 });
  });
});
