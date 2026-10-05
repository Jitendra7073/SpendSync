import { z } from 'zod';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { suggestFor } from '@/planify/service';
import { monthSchema } from '@/types/plan.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/** GET /api/plans/suggestions?month=2026-10: a draft plan from the last 3 months. Nothing is saved. */
export const GET = withApi(
  async (request, { userId }) => {
    const { month } = z.object({ month: monthSchema }).parse({ month: new URL(request.url).searchParams.get('month') ?? undefined });
    return success(await suggestFor(userId, month));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
