import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { checkAssistantLimit } from '@/assistant/limiter';
import { guideNext } from '@/planify/guide.service';
import { guideSchema } from '@/types/plan.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

/** POST /api/plans/guide: the next multiple-choice question of the AI planning guide (or `done`, or `basic` = use the built-in questions). */
export const POST = withApi(
  async (request, { userId }) => {
    const body = guideSchema.parse(await request.json());
    checkAssistantLimit(userId);
    return success(await guideNext(userId, body));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
