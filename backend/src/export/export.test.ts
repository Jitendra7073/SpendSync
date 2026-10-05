import { beforeAll, describe, expect, it, vi } from 'vitest';

vi.stubEnv('DATABASE_URL', 'postgres://user:pass@localhost:5432/test');
vi.stubEnv('AUTH_SECRET', 'x'.repeat(40));
vi.mock('../db/index', () => ({ db: {} }));

import { buildAttachments, holdsCsv, planCsv, transactionsCsv } from './build';

describe('export files', () => {
  const tx = [{ createdAt: new Date('2026-10-05T00:00:00Z'), type: 'debit', category: 'Food', merchant: 'Cafe "Blue"', amount: '250.00', note: 'lunch, with team' }];

  it('quotes CSV safely, including commas and double quotes', () => {
    const out = transactionsCsv(tx);
    expect(out.split('\n')[0]).toBe('"Date","Type","Category","Merchant","Amount","Note"');
    expect(out).toContain('"2026-10-05","Expense","Food","Cafe ""Blue""","250.00","lunch, with team"');
  });

  it('labels holds and plan rows', () => {
    expect(holdsCsv([{ personName: 'Asha', direction: 'owed_to_me', amount: '1500', expectedReturnDate: new Date('2026-10-20T00:00:00Z'), status: 'pending', createdAt: new Date('2026-10-01T00:00:00Z') }])).toContain('"Asha","Owes you","1500","2026-10-20","pending","2026-10-01"');
    expect(planCsv([{ month: '2026-10', bucket: 'Eating out', kind: 'spend', limit: 2000, spent: 800 }])).toContain('"2026-10","Eating out","spend","2000","800"');
  });

  it('makes one file per kind for CSV and one file for JSON', () => {
    const data = { transactions: tx, plan: [] };
    expect(buildAttachments(data, 'csv').map((a) => a.filename)).toEqual(['transactions.csv', 'plan.csv']);
    const json = buildAttachments(data, 'json');
    expect(json).toHaveLength(1);
    expect(JSON.parse(json[0].content).transactions[0].category).toBe('Food');
  });
});

describe('months between', () => {
  let monthsBetween: (a: string, b: string) => string[];
  beforeAll(async () => {
    ({ monthsBetween } = await import('./service'));
  });
  it('lists the months a range touches, capped at twelve', () => {
    expect(monthsBetween('2026-08-15', '2026-10-02')).toEqual(['2026-08', '2026-09', '2026-10']);
    expect(monthsBetween('2025-11-01', '2026-02-01')).toEqual(['2025-11', '2025-12', '2026-01', '2026-02']);
    expect(monthsBetween('2020-01-01', '2026-01-01')).toHaveLength(12);
  });
});
