import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { moveMoney } from '@/planify/service';
import { monthSchema, moveSchema, todayQuerySchema } from '@/types/plan.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/** POST /api/plans/2026-10/move: shift part of one bucket's limit to another (the plan total stays the same). */
export const POST = withApi(
  async (request, { userId, params }) => {
    const month = monthSchema.parse((await params).month);
    const body = moveSchema.parse(await request.json());
    const today = todayQuerySchema.parse({ today: new URL(request.url).searchParams.get('today') ?? undefined }).today ?? new Date().toISOString().slice(0, 10);
    return success(await moveMoney(userId, month, body, today));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
