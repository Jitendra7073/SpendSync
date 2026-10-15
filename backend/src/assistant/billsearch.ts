import { and, desc, eq, gte, ilike, inArray, lte, or, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { billReads, bills, transactions } from '../db/schema/index';
import { liveBill, liveTx } from '../lib/live';
import type { FindBillsInput } from '../types/billmail.types';

export type MatchRow = { id: string; date: Date; amount: number; merchant: string; category: string; note: string | null; billPages: number; read: unknown | null };

/** "₹2,000" matches 1,960–2,040; small amounts get at least ±₹20. */
export function amountWindow(amount: number) {
  const d = Math.max(amount * 0.02, 20);
  return { lo: Math.round((amount - d) * 100) / 100, hi: Math.round((amount + d) * 100) / 100 };
}

const text = (r: MatchRow) => [r.merchant, r.note ?? '', r.category, JSON.stringify(r.read ?? '')].join(' ').toLowerCase();

/** Closest amount first (when asked), then rows whose text matched, then newest. */
export function rankMatches(rows: MatchRow[], q: { amount?: number; query?: string }): MatchRow[] {
  const needle = q.query?.trim().toLowerCase();
  return [...rows].sort((a, b) => {
    if (q.amount !== undefined) {
      const d = Math.abs(a.amount - q.amount) - Math.abs(b.amount - q.amount);
      if (Math.abs(d) > 0.005) return d;
    }
    if (needle) {
      const t = Number(text(b).includes(needle)) - Number(text(a).includes(needle));
      if (t !== 0) return t;
    }
    return b.date.getTime() - a.date.getTime();
  });
}

/** What the model may see from a bill: short, capped, labelled as data by the caller. */
export function fenceBill(read: unknown): { merchant: string | null; total: number | null; items: string[] } | null {
  if (!read || typeof read !== 'object') return null;
  const r = read as { merchant?: unknown; total?: unknown; items?: unknown };
  const items = Array.isArray(r.items) ? r.items : [];
  return {
    merchant: typeof r.merchant === 'string' ? r.merchant.slice(0, 60) : null,
    total: typeof r.total === 'number' && Number.isFinite(r.total) ? r.total : null,
    items: items
      .map((i) => (i && typeof i === 'object' && typeof (i as { name?: unknown }).name === 'string' ? (i as { name: string }).name.slice(0, 60) : null))
      .filter((n): n is string => !!n)
      .slice(0, 5),
  };
}

const billCountSql = sql<number>`(SELECT COUNT(*)::int FROM bills b WHERE b.transaction_id = ${transactions.id} AND b.deleted_at IS NULL AND b.status = 'ready')`;

export async function findBills(userId: string, input: FindBillsInput): Promise<MatchRow[]> {
  const conditions = [eq(transactions.userId, userId), liveTx];
  if (input.start_date) conditions.push(gte(transactions.createdAt, new Date(`${input.start_date}T00:00:00.000Z`)));
  if (input.end_date) conditions.push(lte(transactions.createdAt, new Date(`${input.end_date}T23:59:59.999Z`)));
  if (input.category) conditions.push(ilike(transactions.category, input.category.replace(/[\\%_]/g, (c) => `\\${c}`)));
  if (input.amount !== undefined) {
    const w = amountWindow(input.amount);
    conditions.push(sql`${transactions.amount}::numeric BETWEEN ${w.lo} AND ${w.hi}`);
  }
  if (input.query) {
    const like = `%${input.query.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
    conditions.push(
      or(
        ilike(transactions.merchant, like), ilike(transactions.note, like), ilike(transactions.category, like),
        sql`${billReads.data}->>'merchant' ILIKE ${like}`, sql`(${billReads.data}->'items')::text ILIKE ${like}`,
      )!,
    );
  }
  if (input.has_bill === 'with') conditions.push(sql`${billCountSql} > 0`);
  if (input.has_bill === 'without') conditions.push(sql`${billCountSql} = 0`);

  const rows = await db
    .select({ t: transactions, billPages: billCountSql, read: billReads.data })
    .from(transactions)
    .leftJoin(billReads, and(eq(billReads.transactionId, transactions.id), eq(billReads.status, 'done')))
    .where(and(...conditions))
    .orderBy(desc(transactions.createdAt))
    .limit(50);
  const mapped: MatchRow[] = rows.map((r) => ({
    id: r.t.id, date: r.t.createdAt, amount: Number(r.t.amount), merchant: r.t.merchant, category: r.t.category,
    note: r.t.note, billPages: Number(r.billPages), read: r.read ?? null,
  }));
  return rankMatches(mapped, { amount: input.amount, query: input.query }).slice(0, input.limit);
}

/** The user's live transactions among [ids] that have ≥ 1 ready bill, with file count and stored size. */
export async function selectOwnedWithBills(userId: string, ids: string[]) {
  const rows = await db
    .select({ id: bills.transactionId, files: sql<number>`COUNT(*)::int`, bytes: sql<number>`COALESCE(SUM(${bills.bytes}), 0)::bigint` })
    .from(bills)
    .innerJoin(transactions, eq(transactions.id, bills.transactionId))
    .where(and(eq(bills.userId, userId), inArray(bills.transactionId, ids), eq(bills.status, 'ready'), liveBill, liveTx))
    .groupBy(bills.transactionId);
  return rows.map((r) => ({ id: r.id, files: Number(r.files), bytes: Number(r.bytes) }));
}
