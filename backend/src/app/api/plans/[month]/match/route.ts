import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { answerMatch } from '@/planify/service';
import { matchSchema, monthSchema, todayQuerySchema } from '@/types/plan.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/** POST /api/plans/2026-10/match: answer a Yes/No about what belongs in a bucket (or forget an answer). Returns the refreshed plan. */
export const POST = withApi(
  async (request, { userId, params }) => {
    const month = monthSchema.parse((await params).month);
    const body = matchSchema.parse(await request.json());
    const today = todayQuerySchema.parse({ today: new URL(request.url).searchParams.get('today') ?? undefined }).today ?? new Date().toISOString().slice(0, 10);
    return success(await answerMatch(userId, month, body, today));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
