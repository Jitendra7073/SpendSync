import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { act, remove } from '@/capture/service';
import { actionSchema, messageIdSchema } from '@/types/capture.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/** PATCH /api/capture/messages/:id { action: add | same | different | ignore } */
export const PATCH = withApi(
  async (request, { userId, params }) => {
    const id = messageIdSchema.parse((await params).id);
    const { action } = actionSchema.parse(await request.json());
    return success(await act(userId, id, action));
  },
  { auth: 'required' },
);

/** DELETE /api/capture/messages/:id: delete the saved copy only; its transaction stays. */
export const DELETE = withApi(
  async (_request, { userId, params }) => {
    await remove(userId, messageIdSchema.parse((await params).id));
    return success({ deleted: true });
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
