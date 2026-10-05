import { describe, expect, it } from 'vitest';
import { bucketStatus, computeStatus, monthContext, type BucketInput } from './status';
import { suggestPlan, type MonthHistory } from './suggest';

const b = (over: Partial<BucketInput>): BucketInput => ({ id: 'x', category: 'Food', name: 'Food', kind: 'spend', limit: 1000, spent: 0, sortOrder: 0, rollover: false, ...over });

describe('month context', () => {
  it('counts days and clamps today for past and future months', () => {
    expect(monthContext('2026-10', '2026-10-14')).toEqual({ daysInMonth: 31, day: 14, isPast: false, isFuture: false });
    expect(monthContext('2026-02', '2026-02-03').daysInMonth).toBe(28);
    expect(monthContext('2028-02', '2028-02-03').daysInMonth).toBe(29); // leap year
    expect(monthContext('2026-09', '2026-10-05')).toMatchObject({ day: 30, isPast: true });
    expect(monthContext('2026-11', '2026-10-05')).toMatchObject({ day: 0, isFuture: true });
  });
});

describe('bucket state', () => {
  const ctx = monthContext('2026-10', '2026-10-14');
  it('spend buckets go ok -> close at 80% -> over past the limit (soft: never blocks)', () => {
    expect(bucketStatus(b({ spent: 790 }), ctx).state).toBe('ok');
    expect(bucketStatus(b({ spent: 800 }), ctx).state).toBe('close');
    expect(bucketStatus(b({ spent: 1000 }), ctx).state).toBe('close'); // exactly at the limit is not over yet
    expect(bucketStatus(b({ spent: 1001 }), ctx).state).toBe('over');
    expect(bucketStatus(b({ spent: 1150 }), ctx).remaining).toBe(-150);
  });

  it('fixed buckets are paid when about fully spent and never "close"', () => {
    expect(bucketStatus(b({ kind: 'fixed', limit: 12000, spent: 9600 }), ctx).state).toBe('ok');
    expect(bucketStatus(b({ kind: 'fixed', limit: 12000, spent: 12000 }), ctx).state).toBe('paid');
    expect(bucketStatus(b({ kind: 'fixed', limit: 12000, spent: 12700 }), ctx).state).toBe('over'); // more than 5% above
    expect(bucketStatus(b({ kind: 'fixed', limit: 12000, spent: 12500 }), ctx).state).toBe('paid'); // within 5% still counts as paid
  });

  it('savings are saved once reached and are never over', () => {
    expect(bucketStatus(b({ kind: 'savings', limit: 6000, spent: 2000 }), ctx).state).toBe('ok');
    expect(bucketStatus(b({ kind: 'savings', limit: 6000, spent: 9000 }), ctx).state).toBe('saved');
  });

  it('a zero limit with spending is over, with a capped percent', () => {
    const s = bucketStatus(b({ limit: 0, spent: 50 }), ctx);
    expect(s.state).toBe('over');
    expect(s.percent).toBe(999);
    expect(bucketStatus(b({ limit: 0, spent: 0 }), ctx).state).toBe('ok');
  });
});

describe('pace', () => {
  it('warns when spending is much faster than the month, with the day the limit runs out', () => {
    const ctx = monthContext('2026-10', '2026-10-10'); // 10 of 31 days
    const s = bucketStatus(b({ limit: 2000, spent: 1200 }), ctx); // 60% used in 32% of the month
    expect(s.paceRatio).toBeCloseTo(1.86, 1);
    expect(s.projected).toBe(3720);
    expect(s.runsOutOnDay).toBe(17); // 1200 per 10 days -> 2000 reached on day 17
    expect(s.paceWarning).toBe(true);
  });

  it('stays quiet early in the month, when on pace, and once already over', () => {
    expect(bucketStatus(b({ limit: 2000, spent: 1200 }), monthContext('2026-10', '2026-10-03')).paceWarning).toBe(false); // day 3: too early to say
    expect(bucketStatus(b({ limit: 3100, spent: 1000 }), monthContext('2026-10', '2026-10-10')).paceWarning).toBe(false); // on pace
    expect(bucketStatus(b({ limit: 1000, spent: 1500 }), monthContext('2026-10', '2026-10-10')).paceWarning).toBe(false); // over has its own alert
    expect(bucketStatus(b({ limit: 2000, spent: 1200 }), monthContext('2026-09', '2026-10-10')).paceWarning).toBe(false); // past month
  });
});

describe('plan status', () => {
  const ctx = monthContext('2026-10', '2026-10-14'); // day 14 of 31 -> 18 days left including today
  const buckets: BucketInput[] = [
    b({ id: '1', category: 'Housing', name: 'Rent', kind: 'fixed', limit: 12000, sortOrder: 0 }),
    b({ id: '2', category: 'Groceries', name: 'Groceries', limit: 5000, sortOrder: 1 }),
    b({ id: '3', category: 'Eating out', name: 'Eating out', limit: 2000, sortOrder: 2 }),
    b({ id: '4', category: 'Transport', name: 'Transport', limit: 2500, sortOrder: 3 }),
    b({ id: '5', category: 'Savings', name: 'Emergency', kind: 'savings', limit: 6000, sortOrder: 4 }),
  ];
  const status = computeStatus(
    { income: 45000, carryOver: 0, buckets, spentByCategory: { Housing: 12000, Groceries: 3100, 'Eating out': 1640, Transport: 2650, Savings: 2000, Shopping: 800 } },
    ctx,
  );

  it('adds up what is planned and what is left to plan', () => {
    expect(status.planned).toBe(27500);
    expect(status.leftToPlan).toBe(17500);
    expect(status.daysLeft).toBe(18);
  });

  it('safe to spend uses only everyday buckets that are not over', () => {
    // Groceries 1900 + Eating out 360 = 2260 (Transport is over, Rent and Savings are not everyday money)
    expect(status.safeToSpendToday).toBe(125.56);
  });

  it('counts states and lists spending that has no bucket', () => {
    expect(status.counts).toEqual({ ok: 3, close: 1, over: 1 }); // Rent paid, Groceries ok, Savings ok / Eating out close / Transport over
    expect(status.unplanned).toEqual([{ category: 'Shopping', spent: 800 }]);
    expect(status.unplannedTotal).toBe(800);
  });

  it('carry-over adds to what is available', () => {
    const s = computeStatus({ income: 45000, carryOver: 1500, buckets, spentByCategory: {} }, ctx);
    expect(s.available).toBe(46500);
  });

  it('a finished month has no days left and a future month has all of them', () => {
    expect(computeStatus({ income: 0, carryOver: 0, buckets, spentByCategory: {} }, monthContext('2026-09', '2026-10-05')).safeToSpendToday).toBe(0);
    expect(computeStatus({ income: 0, carryOver: 0, buckets, spentByCategory: {} }, monthContext('2026-11', '2026-10-05')).daysLeft).toBe(30);
  });
});

describe('suggest a plan from history', () => {
  const month = (m: string, byCategory: MonthHistory['byCategory'], credits: number[]): MonthHistory => ({ month: m, byCategory, credits });
  const history = [
    month('2026-07', { Housing: { spent: 12000, count: 1 }, Food: { spent: 4600, count: 31 }, Gym: { spent: 1200, count: 1 }, Trip: { spent: 9000, count: 2 } }, [44000, 500]),
    month('2026-08', { Housing: { spent: 12000, count: 1 }, Food: { spent: 5200, count: 28 }, Gym: { spent: 1200, count: 1 }, Savings: { spent: 5000, count: 1 } }, [45000]),
    month('2026-09', { Housing: { spent: 12000, count: 1 }, Food: { spent: 4900, count: 30 }, Gym: { spent: 1200, count: 1 }, Savings: { spent: 5000, count: 1 } }, [45000, 300]),
  ];
  const s = suggestPlan(history);
  const by = (c: string) => s.items.find((i) => i.category === c)!;

  it('finds bills by repetition and keeps everyday spending as an average', () => {
    expect(by('Housing')).toMatchObject({ kind: 'fixed', limit: 12000 });
    expect(by('Gym')).toMatchObject({ kind: 'fixed', limit: 1200 });
    expect(by('Food')).toMatchObject({ kind: 'spend', limit: 4900, average: 4900 }); // many payments, so not a bill
  });

  it('recognises savings by name and drops one-off spends', () => {
    expect(by('Savings').kind).toBe('savings');
    expect(s.items.some((i) => i.category === 'Trip')).toBe(false);
  });

  it('orders fixed, then everyday (largest first), then savings, and numbers them', () => {
    expect(s.items.map((i) => i.kind)).toEqual(['fixed', 'fixed', 'spend', 'savings']);
    expect(s.items.map((i) => i.sortOrder)).toEqual([0, 1, 2, 3]);
  });

  it('takes the salary as the typical biggest credit', () => {
    expect(s.suggestedIncome).toBe(45000);
    expect(s.monthsUsed).toBe(3);
  });

  it('copes with no history and with a single month', () => {
    expect(suggestPlan([])).toEqual({ items: [], suggestedIncome: 0, monthsUsed: 0, confidence: 'low' });
    const one = suggestPlan([history[0]]);
    expect(one.items.some((i) => i.category === 'Trip')).toBe(true); // with one month there is nothing to compare against
  });
});
