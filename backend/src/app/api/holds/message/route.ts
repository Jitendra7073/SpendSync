import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { checkAssistantLimit } from '@/assistant/limiter';
import { draftMessage } from '@/holds/message.service';
import { messageRequestSchema } from '@/holds/message';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

/** POST /api/holds/message: an AI-written follow-up draft for a hold, or `source: "none"` (the app then uses its own template). Nothing is sent. */
export const POST = withApi(
  async (request, { userId }) => {
    const body = messageRequestSchema.parse(await request.json());
    checkAssistantLimit(userId);
    return success(await draftMessage(body));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
