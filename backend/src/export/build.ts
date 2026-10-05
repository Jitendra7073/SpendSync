/** Turns the user's own data into CSV / JSON text for an emailed export. Plain functions, no database. */

const quote = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
const csv = (header: string[], rows: unknown[][]) => [header.map(quote).join(','), ...rows.map((r) => r.map(quote).join(','))].join('\n') + '\n';

export interface TxRow { createdAt: Date; type: string; category: string; merchant: string; amount: string | number; note: string | null }
export interface HoldRow { personName: string; direction: string; amount: string | number; expectedReturnDate: Date; status: string; createdAt: Date }
export interface PlanRow { month: string; bucket: string; kind: string; limit: number; spent: number }

const day = (d: Date) => d.toISOString().slice(0, 10);

export const transactionsCsv = (rows: TxRow[]) =>
  csv(['Date', 'Type', 'Category', 'Merchant', 'Amount', 'Note'], rows.map((r) => [day(r.createdAt), r.type === 'credit' ? 'Income' : 'Expense', r.category, r.merchant, r.amount, r.note ?? '']));

export const holdsCsv = (rows: HoldRow[]) =>
  csv(['Person', 'Direction', 'Amount', 'Due date', 'Status', 'Created'], rows.map((r) => [r.personName, r.direction === 'owed_to_me' ? 'Owes you' : 'You owe', r.amount, day(r.expectedReturnDate), r.status, day(r.createdAt)]));

export const planCsv = (rows: PlanRow[]) => csv(['Month', 'Bucket', 'Kind', 'Limit', 'Spent'], rows.map((r) => [r.month, r.bucket, r.kind, r.limit, r.spent]));

export interface ExportInput {
  transactions?: TxRow[];
  holds?: HoldRow[];
  plan?: PlanRow[];
}

/** One attachment per kind for CSV, or a single JSON file holding everything. */
export function buildAttachments(data: ExportInput, format: 'csv' | 'json'): { filename: string; content: string; contentType: string }[] {
  if (format === 'json') {
    return [{ filename: 'spendsync-export.json', content: JSON.stringify({ exportedAt: new Date().toISOString(), ...data }, null, 2), contentType: 'application/json' }];
  }
  const out: { filename: string; content: string; contentType: string }[] = [];
  if (data.transactions) out.push({ filename: 'transactions.csv', content: transactionsCsv(data.transactions), contentType: 'text/csv' });
  if (data.holds) out.push({ filename: 'holds.csv', content: holdsCsv(data.holds), contentType: 'text/csv' });
  if (data.plan) out.push({ filename: 'plan.csv', content: planCsv(data.plan), contentType: 'text/csv' });
  return out;
}
