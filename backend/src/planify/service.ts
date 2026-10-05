import { and, eq, gte, inArray, lt, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { budgets, planEvents, plans, transactions } from '../db/schema/index';
import type { MoveInput, SavePlanInput } from '../types/plan.types';
import { BadRequestError, NotFoundError } from '../utils/errors';
import { computeStatus, monthContext, type BucketInput, type BucketKind, type PlanStatus } from './status';
import { suggestPlan, type MonthHistory, type Suggestion } from './suggest';

const num = (v: unknown) => (v === null || v === undefined ? 0 : Number(v));
const kindOf = (k: string): BucketKind => (k === 'fixed' || k === 'savings' ? k : 'spend');

/** [start, end) of a calendar month in UTC (the app stores each transaction's day as UTC midnight). */
export function monthRange(month: string) {
  const [y, m] = month.split('-').map(Number);
  return { start: new Date(Date.UTC(y, m - 1, 1)), end: new Date(Date.UTC(y, m, 1)) };
}

/** The `n` months before `month`, oldest first: ('2026-10', 3) gives 2026-07, 2026-08, 2026-09. */
export function previousMonths(month: string, n: number): string[] {
  const [y, m] = month.split('-').map(Number);
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(Date.UTC(y, m - 1 - (n - i), 1));
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
  });
}

/** Net spend per category for a month: debits minus refunds (credits), never below zero. */
async function netSpentByCategory(userId: string, month: string): Promise<Record<string, number>> {
  const { start, end } = monthRange(month);
  const rows = await db
    .select({
      category: transactions.category,
      debit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'debit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
      credit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'credit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
    })
    .from(transactions)
    .where(and(eq(transactions.userId, userId), gte(transactions.createdAt, start), lt(transactions.createdAt, end)))
    .groupBy(transactions.category);
  const out: Record<string, number> = {};
  for (const r of rows) out[r.category] = Math.max(0, num(r.debit) - num(r.credit));
  return out;
}

export interface PlanView {
  month: string;
  /** True when the user has a plan header or at least one bucket for this month. */
  exists: boolean;
  /** False for plans that only exist as old budgets (no income was ever entered). */
  hasIncome: boolean;
  income: number;
  carryOver: number;
  status: PlanStatus;
}

export async function loadPlan(userId: string, month: string, today: string): Promise<PlanView> {
  const [plan] = await db.select().from(plans).where(and(eq(plans.userId, userId), eq(plans.month, month))).limit(1);
  const rows = await db
    .select()
    .from(budgets)
    .where(and(eq(budgets.userId, userId), eq(budgets.month, month)))
    .orderBy(budgets.sortOrder, budgets.category);
  const spent = await netSpentByCategory(userId, month);

  const buckets: BucketInput[] = rows.map((r) => ({
    id: r.id,
    category: r.category,
    name: r.name ?? r.category,
    kind: kindOf(r.kind),
    limit: num(r.limitAmount),
    spent: 0,
    sortOrder: r.sortOrder,
    rollover: r.rollover,
  }));
  const income = plan ? num(plan.income) : 0;
  const carryOver = plan ? num(plan.carryOver) : 0;
  return {
    month,
    exists: !!plan || rows.length > 0,
    hasIncome: income + carryOver > 0,
    income,
    carryOver,
    status: computeStatus({ income, carryOver, buckets, spentByCategory: spent }, monthContext(month, today)),
  };
}

/** Saves the income and replaces the month's buckets with exactly the ones given. */
export async function savePlan(userId: string, month: string, input: SavePlanInput, today: string): Promise<PlanView> {
  await db.transaction(async (tx) => {
    await tx
      .insert(plans)
      .values({ userId, month, income: input.income.toFixed(2), carryOver: input.carryOver.toFixed(2) })
      .onConflictDoUpdate({
        target: [plans.userId, plans.month],
        set: { income: input.income.toFixed(2), carryOver: input.carryOver.toFixed(2), status: 'active', updatedAt: new Date() },
      });

    const existing = await tx.select().from(budgets).where(and(eq(budgets.userId, userId), eq(budgets.month, month)));
    const keep = new Set(input.items.map((i) => i.category));
    const drop = existing.filter((e) => !keep.has(e.category)).map((e) => e.id);
    if (drop.length) await tx.delete(budgets).where(and(eq(budgets.userId, userId), inArray(budgets.id, drop)));

    for (const [index, item] of input.items.entries()) {
      const values = {
        kind: item.kind,
        name: item.name ?? item.category,
        limitAmount: item.limitAmount.toFixed(2),
        sortOrder: item.sortOrder || index,
        rollover: item.rollover,
        updatedAt: new Date(),
      };
      const found = existing.find((e) => e.category === item.category);
      if (found) await tx.update(budgets).set(values).where(eq(budgets.id, found.id));
      else await tx.insert(budgets).values({ userId, month, category: item.category, ...values });
    }
  });
  return loadPlan(userId, month, today);
}

/** Removes the month's plan and its buckets. Transactions are untouched; spending just stops counting against limits. */
export async function deletePlan(userId: string, month: string, today: string): Promise<PlanView> {
  await db.transaction(async (tx) => {
    await tx.delete(budgets).where(and(eq(budgets.userId, userId), eq(budgets.month, month)));
    await tx.delete(plans).where(and(eq(plans.userId, userId), eq(plans.month, month)));
  });
  return loadPlan(userId, month, today);
}

/** Moves part of one bucket's limit to another. The plan total stays the same. */
export async function moveMoney(userId: string, month: string, input: MoveInput, today: string): Promise<PlanView> {
  await db.transaction(async (tx) => {
    const rows = await tx
      .select()
      .from(budgets)
      .where(and(eq(budgets.userId, userId), eq(budgets.month, month), inArray(budgets.category, [input.fromCategory, input.toCategory])));
    const from = rows.find((r) => r.category === input.fromCategory);
    const to = rows.find((r) => r.category === input.toCategory);
    if (!from || !to) throw new NotFoundError('Bucket not found');
    if (num(from.limitAmount) < input.amount) throw new BadRequestError('That bucket does not have that much planned');

    await tx.update(budgets).set({ limitAmount: (num(from.limitAmount) - input.amount).toFixed(2), updatedAt: new Date() }).where(eq(budgets.id, from.id));
    await tx.update(budgets).set({ limitAmount: (num(to.limitAmount) + input.amount).toFixed(2), updatedAt: new Date() }).where(eq(budgets.id, to.id));
    await tx.insert(planEvents).values({
      userId,
      month,
      kind: 'move',
      fromCategory: input.fromCategory,
      toCategory: input.toCategory,
      amount: input.amount.toFixed(2),
    });
  });
  return loadPlan(userId, month, today);
}

/** A first draft of `month` from the 3 months before it. Nothing is saved. */
export async function loadHistory(userId: string, month: string): Promise<MonthHistory[]> {
  const months = previousMonths(month, 3);
  const start = monthRange(months[0]).start;
  const end = monthRange(month).start;
  const monthKey = sql<string>`to_char(${transactions.createdAt}, 'YYYY-MM')`;

  const spendRows = await db
    .select({
      month: monthKey,
      category: transactions.category,
      debit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'debit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
      credit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'credit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
      count: sql<number>`COUNT(CASE WHEN ${transactions.type} = 'debit' THEN 1 END)`,
    })
    .from(transactions)
    .where(and(eq(transactions.userId, userId), gte(transactions.createdAt, start), lt(transactions.createdAt, end)))
    .groupBy(monthKey, transactions.category);

  const creditRows = await db
    .select({ month: monthKey, amount: transactions.amount })
    .from(transactions)
    .where(and(eq(transactions.userId, userId), eq(transactions.type, 'credit'), gte(transactions.createdAt, start), lt(transactions.createdAt, end)));

  const history: MonthHistory[] = months
    .map((m) => ({
      month: m,
      byCategory: Object.fromEntries(
        spendRows.filter((r) => r.month === m).map((r) => [r.category, { spent: Math.max(0, num(r.debit) - num(r.credit)), count: num(r.count) }]),
      ),
      credits: creditRows.filter((r) => r.month === m).map((r) => num(r.amount)),
    }))
    // months before the user started tracking carry no information
    .filter((h) => Object.keys(h.byCategory).length > 0 || h.credits.length > 0);
  return history;
}

export async function suggestFor(userId: string, month: string): Promise<Suggestion> {
  return suggestPlan(await loadHistory(userId, month));
}
