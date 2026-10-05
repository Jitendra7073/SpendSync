import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { loadPlan, savePlan } from '@/planify/service';
import { monthSchema, savePlanSchema, todayQuerySchema } from '@/types/plan.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const todayOf = (request: Request) =>
  todayQuerySchema.parse({ today: new URL(request.url).searchParams.get('today') ?? undefined }).today ?? new Date().toISOString().slice(0, 10);

/** GET /api/plans/2026-10?today=2026-10-14: the month's plan with live status (spent, left, pace, safe to spend). */
export const GET = withApi(
  async (request, { userId, params }) => {
    const month = monthSchema.parse((await params).month);
    return success(await loadPlan(userId, month, todayOf(request)));
  },
  { auth: 'required' },
);

/** PUT /api/plans/2026-10: save the income and replace the month's buckets with exactly the ones sent. */
export const PUT = withApi(
  async (request, { userId, params }) => {
    const month = monthSchema.parse((await params).month);
    const body = savePlanSchema.parse(await request.json());
    return success(await savePlan(userId, month, body, todayOf(request)));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
