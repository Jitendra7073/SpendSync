/**
 * Planify maths. Pure functions only (no database, no clock), so the same rules can be tested exhaustively and
 * mirrored exactly in the Android app (`PlanEvaluator.kt` uses the same vectors).
 *
 * Words: a plan has BUCKETS. A bucket tracks one transaction category with a limit.
 *   fixed   - bills that repeat (rent, EMI). Never "close"; "paid" once ~fully spent.
 *   spend   - everyday money with a soft limit. ok -> close (80%) -> over.
 *   savings - a target to put aside. "saved" once reached; never "over".
 */
export type BucketKind = 'fixed' | 'spend' | 'savings';
export type BucketState = 'ok' | 'close' | 'over' | 'paid' | 'saved';

export const CLOSE_AT = 80; // percent
export const PACE_ALERT_RATIO = 1.3;
export const PACE_MIN_DAY = 5;

export interface BucketInput {
  id: string;
  category: string;
  name: string;
  kind: BucketKind;
  limit: number;
  spent: number;
  sortOrder: number;
  rollover: boolean;
}

export interface BucketStatus extends BucketInput {
  remaining: number;
  /** Capped at 999 so a zero limit with spending does not produce Infinity. */
  percent: number;
  state: BucketState;
  /** (spent / limit) / (elapsed / daysInMonth). 1 = exactly on pace. Null when it means nothing yet. */
  paceRatio: number | null;
  /** Where this bucket lands at month end if the spending rate continues. */
  projected: number | null;
  /** Day of the month the limit would run out at the current rate; null if it will not, or is already over. */
  runsOutOnDay: number | null;
  /** True when it is on course to overrun even though it is still within the limit. */
  paceWarning: boolean;
}

export interface MonthContext {
  daysInMonth: number;
  /** Day of the month today falls on, clamped: 0 before the month starts, daysInMonth after it ended. */
  day: number;
  isPast: boolean;
  isFuture: boolean;
}

const round2 = (n: number) => Math.round(n * 100) / 100;

/** `month` is YYYY-MM, `today` is YYYY-MM-DD in the user's own calendar. */
export function monthContext(month: string, today: string): MonthContext {
  const [y, m] = month.split('-').map(Number);
  const daysInMonth = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const todayMonth = today.slice(0, 7);
  if (todayMonth < month) return { daysInMonth, day: 0, isPast: false, isFuture: true };
  if (todayMonth > month) return { daysInMonth, day: daysInMonth, isPast: true, isFuture: false };
  return { daysInMonth, day: Number(today.slice(8, 10)), isPast: false, isFuture: false };
}

export function bucketStatus(b: BucketInput, ctx: MonthContext): BucketStatus {
  const { limit, spent, kind } = b;
  const remaining = round2(limit - spent);
  const percent = limit > 0 ? Math.min(999, round2((spent / limit) * 100)) : spent > 0 ? 999 : 0;

  let state: BucketState;
  if (kind === 'savings') state = spent >= limit && limit > 0 ? 'saved' : 'ok';
  else if (kind === 'fixed') state = spent > limit * 1.05 ? 'over' : spent >= limit * 0.95 && limit > 0 ? 'paid' : 'ok';
  else state = spent > limit ? 'over' : percent >= CLOSE_AT ? 'close' : 'ok';

  let paceRatio: number | null = null;
  let projected: number | null = null;
  let runsOutOnDay: number | null = null;
  if (kind === 'spend' && ctx.day >= 1 && !ctx.isPast) {
    const rate = spent / ctx.day;
    projected = round2(rate * ctx.daysInMonth);
    if (limit > 0) paceRatio = round2(spent / limit / (ctx.day / ctx.daysInMonth));
    if (rate > 0 && limit > spent) {
      const day = Math.ceil(limit / rate);
      runsOutOnDay = day <= ctx.daysInMonth ? day : null;
    }
  }
  const paceWarning =
    kind === 'spend' && state !== 'over' && ctx.day >= PACE_MIN_DAY && !ctx.isPast && paceRatio !== null && paceRatio >= PACE_ALERT_RATIO && (projected ?? 0) > limit;

  return { ...b, remaining, percent, state, paceRatio, projected, runsOutOnDay, paceWarning };
}

export interface PlanInput {
  income: number;
  carryOver: number;
  buckets: BucketInput[];
  /** Net spend per category for the month, including categories that have no bucket. */
  spentByCategory: Record<string, number>;
}

export interface PlanStatus {
  daysInMonth: number;
  day: number;
  daysLeft: number;
  monthProgressPercent: number;
  available: number;
  planned: number;
  /** available - planned. Positive = money still unassigned; negative = planned more than you have. */
  leftToPlan: number;
  spentInPlan: number;
  buckets: BucketStatus[];
  counts: { ok: number; close: number; over: number };
  /** What is safe to spend today across everyday buckets that are not over. */
  safeToSpendToday: number;
  unplanned: Array<{ category: string; spent: number }>;
  unplannedTotal: number;
}

export function computeStatus(input: PlanInput, ctx: MonthContext): PlanStatus {
  const buckets = [...input.buckets]
    .sort((a, b) => a.sortOrder - b.sortOrder || a.category.localeCompare(b.category))
    .map((b) => bucketStatus({ ...b, spent: round2(input.spentByCategory[b.category] ?? 0) }, ctx));

  const available = round2(input.income + input.carryOver);
  const planned = round2(buckets.reduce((s, b) => s + b.limit, 0));
  // Today still counts; a month that has not started has all of its days ahead.
  const daysLeft = ctx.isPast ? 0 : ctx.isFuture ? ctx.daysInMonth : Math.max(1, ctx.daysInMonth - ctx.day + 1);

  const flexRemaining = buckets
    .filter((b) => b.kind === 'spend' && b.state !== 'over')
    .reduce((s, b) => s + Math.max(0, b.remaining), 0);

  const planned_categories = new Set(buckets.map((b) => b.category));
  const unplanned = Object.entries(input.spentByCategory)
    .filter(([category, spent]) => spent > 0 && !planned_categories.has(category))
    .map(([category, spent]) => ({ category, spent: round2(spent) }))
    .sort((a, b) => b.spent - a.spent);

  return {
    daysInMonth: ctx.daysInMonth,
    day: ctx.day,
    daysLeft,
    monthProgressPercent: ctx.daysInMonth > 0 ? round2((ctx.day / ctx.daysInMonth) * 100) : 0,
    available,
    planned,
    leftToPlan: round2(available - planned),
    spentInPlan: round2(buckets.reduce((s, b) => s + b.spent, 0)),
    buckets,
    counts: {
      ok: buckets.filter((b) => b.state === 'ok' || b.state === 'paid' || b.state === 'saved').length,
      close: buckets.filter((b) => b.state === 'close').length,
      over: buckets.filter((b) => b.state === 'over').length,
    },
    safeToSpendToday: daysLeft > 0 ? round2(flexRemaining / daysLeft) : 0,
    unplanned,
    unplannedTotal: round2(unplanned.reduce((s, u) => s + u.spent, 0)),
  };
}
