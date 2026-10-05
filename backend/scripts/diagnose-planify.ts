/**
 * End-to-end check of the Planify API against a RUNNING local server (default http://localhost:3114), using
 * throwaway accounts and seeded transactions that are deleted at the end.
 *
 *   RATE_LIMIT_MAX_REQUESTS=5000 npx next dev -p 3114
 *   npx vite-node scripts/diagnose-planify.ts
 */
import { config as loadEnv } from 'dotenv';

loadEnv({ path: '.env' });
const BASE = process.env.DIAG_BASE ?? 'http://localhost:3114';

type Res = { status: number; json: any };
const results: Array<{ name: string; ok: boolean; detail: string }> = [];

async function call(method: string, path: string, token: string | null, body?: unknown): Promise<Res> {
  const r = await fetch(BASE + path, {
    method,
    headers: { ...(body !== undefined ? { 'content-type': 'application/json' } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json: any = null;
  try { json = JSON.parse(text); } catch { json = text.slice(0, 100); }
  return { status: r.status, json };
}
function check(name: string, ok: boolean, detail = '') {
  results.push({ name, ok, detail });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  [${detail}]` : ''}`);
}
const section = (s: string) => console.log(`\n== ${s}`);
const near = (a: number, b: number, eps = 0.02) => Math.abs(a - b) <= eps;

const pad = (n: number) => String(n).padStart(2, '0');
const now = new Date();
const Y = now.getUTCFullYear();
const M = now.getUTCMonth() + 1;
const D = now.getUTCDate();
const ym = (y: number, m: number) => {
  const d = new Date(Date.UTC(y, m - 1, 1));
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}`;
};
const month = ym(Y, M);
const today = `${month}-${pad(D)}`;
const daysInMonth = new Date(Date.UTC(Y, M, 0)).getUTCDate();

async function signUp(): Promise<{ email: string; token: string }> {
  const email = `plan-${Date.now().toString(36)}${Math.floor(Math.random() * 1e5)}@example.com`;
  const r = await fetch(BASE + '/api/auth/sign-up/email', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, password: 'Probe-pass-12345', name: 'Plan Probe' }) });
  return { email, token: r.headers.get('set-auth-token') ?? '' };
}

async function main() {
  const a = await signUp();
  const b = await signUp();
  if (!a.token || !b.token) throw new Error('could not create the probe accounts');
  const A = a.token;
  const tx = (token: string, body: Record<string, unknown>) => call('POST', '/api/transactions', token, body);
  const at = (y: number, m: number, d: number, h = 9) => `${ym(y, m)}-${pad(d)}T${pad(h)}:00:00.000Z`;

  // ── seed: 3 previous months of habits, then this month so far ──────────────
  section('Seeding history');
  for (let back = 3; back >= 1; back--) {
    const d = new Date(Date.UTC(Y, M - 1 - back, 1));
    const y = d.getUTCFullYear();
    const m = d.getUTCMonth() + 1;
    await tx(A, { amount: 45000, type: 'credit', merchant: 'ACME payroll', category: 'Salary', transactionDate: at(y, m, 1) });
    await tx(A, { amount: 12000, type: 'debit', merchant: 'Landlord', category: 'Housing', transactionDate: at(y, m, 2) });
    await tx(A, { amount: 1200, type: 'debit', merchant: 'Gym', category: 'Gym', transactionDate: at(y, m, 5) });
    for (let k = 0; k < 8; k++) await tx(A, { amount: 500 + back * 20, type: 'debit', merchant: 'Cafe', category: 'Food', transactionDate: at(y, m, 3 + k * 3) });
  }
  // a payment at 23:30 on the LAST day of the previous month (the old dashboard dropped these)
  const prev = new Date(Date.UTC(Y, M - 2, 1));
  const py = prev.getUTCFullYear();
  const pm = prev.getUTCMonth() + 1;
  const lastPrev = new Date(Date.UTC(Y, M - 1, 0)).getUTCDate();
  await tx(A, { amount: 77, type: 'debit', merchant: 'Late', category: 'Boundary', transactionDate: at(py, pm, lastPrev, 23) });
  // this month, up to today
  const dayMax = Math.max(1, Math.min(D, 4));
  await tx(A, { amount: 12000, type: 'debit', merchant: 'Landlord', category: 'Housing', transactionDate: at(Y, M, 1) });
  await tx(A, { amount: 1500, type: 'debit', merchant: 'Cafe', category: 'Food', transactionDate: at(Y, M, 1) });
  await tx(A, { amount: 840, type: 'debit', merchant: 'Swiggy', category: 'Eating out', transactionDate: at(Y, M, dayMax) });
  await tx(A, { amount: 800, type: 'debit', merchant: 'Swiggy', category: 'Eating out', transactionDate: at(Y, M, dayMax) });
  await tx(A, { amount: 2650, type: 'debit', merchant: 'Uber', category: 'Transport', transactionDate: at(Y, M, dayMax) });
  await tx(A, { amount: 800, type: 'debit', merchant: 'Mall', category: 'Shopping', transactionDate: at(Y, M, dayMax) });
  await tx(A, { amount: 2000, type: 'debit', merchant: 'Bank', category: 'Savings', transactionDate: at(Y, M, dayMax) });
  await tx(A, { amount: 300, type: 'credit', merchant: 'Refund', category: 'Eating out', transactionDate: at(Y, M, dayMax) }); // a refund lowers that bucket
  console.log(`  seeded for ${month}, today ${today}`);

  section('Before any plan exists');
  const empty = await call('GET', `/api/plans/${month}?today=${today}`, A);
  check('no plan yet: exists is false and nothing is planned', empty.status === 200 && empty.json?.data?.exists === false && empty.json?.data?.status?.planned === 0);
  check('spending with no bucket shows up as "unplanned"', (empty.json?.data?.status?.unplanned ?? []).some((u: any) => u.category === 'Food'));
  check('unauthenticated request is refused', (await call('GET', `/api/plans/${month}`, null)).status === 401);
  check('a malformed month is refused (422)', (await call('GET', '/api/plans/2026-13', A)).status === 422);

  section('Suggestion from history');
  const sug = await call('GET', `/api/plans/suggestions?month=${month}`, A);
  const items: any[] = sug.json?.data?.items ?? [];
  const find = (c: string) => items.find((i) => i.category === c);
  check('suggestion works', sug.status === 200 && items.length > 0, `HTTP ${sug.status}, ${items.length} items`);
  check('rent is found as a fixed bill at 12,000', find('Housing')?.kind === 'fixed' && find('Housing')?.limit === 12000);
  check('the gym is found as a fixed bill', find('Gym')?.kind === 'fixed');
  check('food (many small payments) stays an everyday bucket near its average', find('Food')?.kind === 'spend' && find('Food')?.limit >= 4000 && find('Food')?.limit <= 4600, `${find('Food')?.limit}`);
  check('the salary is read from the biggest credit', sug.json?.data?.suggestedIncome === 45000);
  check('this month is not used as history', !items.some((i) => i.category === 'Shopping'));

  section('Saving a plan');
  const plan = {
    income: 45000,
    carryOver: 0,
    items: [
      { category: 'Housing', name: 'Rent', kind: 'fixed', limitAmount: 12000 },
      { category: 'Gym', kind: 'fixed', limitAmount: 1200 },
      { category: 'Food', name: 'Groceries', kind: 'spend', limitAmount: 5000 },
      { category: 'Eating out', kind: 'spend', limitAmount: 2000 },
      { category: 'Transport', kind: 'spend', limitAmount: 2500 },
      { category: 'Savings', name: 'Emergency fund', kind: 'savings', limitAmount: 6000 },
    ],
  };
  const saved = await call('PUT', `/api/plans/${month}?today=${today}`, A, plan);
  const st = saved.json?.data?.status;
  const bk = (c: string) => st?.buckets?.find((x: any) => x.category === c);
  check('PUT saves the plan', saved.status === 200 && saved.json?.data?.exists === true && saved.json?.data?.hasIncome === true, `HTTP ${saved.status}`);
  check('planned adds up and left-to-plan is income minus planned', st?.planned === 28700 && st?.leftToPlan === 16300);
  check('buckets keep their kind, display name and order', bk('Housing')?.name === 'Rent' && bk('Savings')?.kind === 'savings' && st?.buckets?.[0]?.category === 'Housing');
  check('rent is "paid", food is "ok"', bk('Housing')?.state === 'paid' && bk('Food')?.state === 'ok');
  check('eating out is "close" and the refund lowered it (1640 - 300 = 1340)', near(bk('Eating out')?.spent, 1340) && bk('Eating out')?.state === (1340 / 2000 >= 0.8 ? 'close' : 'ok'), `spent ${bk('Eating out')?.spent}`);
  check('transport is "over" by 150', bk('Transport')?.state === 'over' && near(bk('Transport')?.remaining, -150));
  check('savings progress counts the transfer (2000 of 6000)', bk('Savings')?.spent === 2000 && bk('Savings')?.state === 'ok');
  check('spend with no bucket is listed as unplanned (Shopping 800)', (st?.unplanned ?? []).some((u: any) => u.category === 'Shopping' && u.spent === 800));
  const daysLeft = daysInMonth - D + 1;
  const flex = Math.max(0, 5000 - 1500) + Math.max(0, 2000 - 1340); // transport is over, so it is left out
  check('safe-to-spend today = everyday money left / days left (today counts)', near(st?.safeToSpendToday, Math.round((flex / daysLeft) * 100) / 100, 0.02), `${st?.safeToSpendToday} vs ${(flex / daysLeft).toFixed(2)}`);
  check('days left counts today', st?.daysLeft === daysLeft, `${st?.daysLeft} vs ${daysLeft}`);

  section('Validation');
  check('two buckets for the same category are refused (422)', (await call('PUT', `/api/plans/${month}`, A, { ...plan, items: [plan.items[0], plan.items[0]] })).status === 422);
  check('a negative limit is refused (422)', (await call('PUT', `/api/plans/${month}`, A, { ...plan, items: [{ category: 'X', limitAmount: -5 }] })).status === 422);
  check('more than 40 buckets are refused (422)', (await call('PUT', `/api/plans/${month}`, A, { ...plan, items: Array.from({ length: 41 }, (_, i) => ({ category: `C${i}`, limitAmount: 1 })) })).status === 422);
  check('an unknown kind is refused (422)', (await call('PUT', `/api/plans/${month}`, A, { ...plan, items: [{ category: 'X', kind: 'wild', limitAmount: 1 }] })).status === 422);
  check('the failed saves changed nothing', (await call('GET', `/api/plans/${month}?today=${today}`, A)).json?.data?.status?.planned === 28700);

  section('Move money');
  const mv = await call('POST', `/api/plans/${month}/move?today=${today}`, A, { fromCategory: 'Food', toCategory: 'Eating out', amount: 500 });
  const ms = mv.json?.data?.status;
  const mb = (c: string) => ms?.buckets?.find((x: any) => x.category === c);
  check('moving money works', mv.status === 200 && mb('Food')?.limit === 4500 && mb('Eating out')?.limit === 2500);
  check('the plan total is unchanged by a move', ms?.planned === 28700);
  check('the receiving bucket is no longer "close" (1340 of 2500 = 54%)', mb('Eating out')?.state === 'ok');
  check('moving more than the bucket has is refused (400)', (await call('POST', `/api/plans/${month}/move`, A, { fromCategory: 'Gym', toCategory: 'Food', amount: 99999 })).status === 400);
  check('moving to a bucket that does not exist is refused (404)', (await call('POST', `/api/plans/${month}/move`, A, { fromCategory: 'Food', toCategory: 'Nope', amount: 10 })).status === 404);
  check('moving to the same bucket is refused (422)', (await call('POST', `/api/plans/${month}/move`, A, { fromCategory: 'Food', toCategory: 'Food', amount: 10 })).status === 422);

  section('Replacing the buckets');
  const trimmed = await call('PUT', `/api/plans/${month}?today=${today}`, A, { ...plan, items: plan.items.filter((i) => i.category !== 'Gym') });
  check('a bucket that is left out is removed, the rest keep their new values', trimmed.json?.data?.status?.buckets?.length === 5 && !trimmed.json?.data?.status?.buckets?.some((x: any) => x.category === 'Gym'));
  const resaved = await call('PUT', `/api/plans/${month}?today=${today}`, A, { ...plan, items: plan.items.filter((i) => i.category !== 'Gym') });
  check('saving the same plan twice does not duplicate buckets', resaved.json?.data?.status?.buckets?.length === 5);

  section('Time');
  const first = await call('GET', `/api/plans/${month}?today=${month}-01`, A);
  check('on day 1 of the month the whole month is ahead', first.json?.data?.status?.daysLeft === daysInMonth && first.json?.data?.status?.day === 1);
  const past = await call('GET', `/api/plans/${ym(py, pm)}?today=${today}`, A);
  check('a finished month has 0 days left and 0 safe-to-spend', past.json?.data?.status?.daysLeft === 0 && past.json?.data?.status?.safeToSpendToday === 0);
  check('the payment at 23:00 on the last day of the month is counted', (past.json?.data?.status?.unplanned ?? []).some((u: any) => u.category === 'Boundary'));
  const dash = await call('GET', `/api/dashboard/summary?month=${ym(py, pm)}`, A);
  check('the dashboard also counts that last-day payment now', (dash.json?.data?.categoryBreakdown ?? []).some((c: any) => c.category === 'Boundary'));

  section('Still works with the old budget screens');
  const bud = await call('GET', `/api/budgets?month=${month}`, A);
  check('the buckets are the month\'s budgets (old endpoint still lists them)', bud.status === 200 && (bud.json?.data ?? []).length === 5);
  const dsum = await call('GET', `/api/dashboard/summary?month=${month}`, A);
  check('the dashboard total budget equals the plan total (28,700 minus the removed 1,200 gym bucket)', near(dsum.json?.data?.totals?.totalBudget, 27500));

  section('Privacy between users');
  const other = await call('GET', `/api/plans/${month}?today=${today}`, b.token);
  check('another user sees no plan for the same month', other.json?.data?.exists === false && other.json?.data?.status?.planned === 0);
  const hijack = await call('POST', `/api/plans/${month}/move`, b.token, { fromCategory: 'Food', toCategory: 'Eating out', amount: 10 });
  check('another user cannot move money in your plan (404)', hijack.status === 404);

  section('Clean-up');
  let removed = 0;
  for (const t of [A, b.token]) if ((await call('DELETE', '/api/account', t)).status === 200) removed++;
  check('both throwaway accounts (and their plans, budgets and transactions) are deleted', removed === 2);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  if (failed.length) { console.log('FAILED:'); for (const f of failed) console.log(`  - ${f.name} ${f.detail}`); }
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => { console.error('Diagnosis crashed:', e); process.exit(2); });
