import { withApi, corsPreflight } from '@/lib/api-handler';
import { created } from '@/lib/response';
import { feedbackSchema, saveFeedback } from '@/assistant/feedback';

/** POST /api/assistant/feedback: thumbs up/down (+ reasons) on an assistant answer. */
export const POST = withApi(
  async (request, { userId }) => {
    const body = feedbackSchema.parse(await request.json());
    await saveFeedback(userId, body);
    return created({ saved: true });
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
