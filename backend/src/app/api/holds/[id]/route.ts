import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, noContent } from '@/lib/response';
import { holdService } from '@/services/hold.service';
import { holdIdSchema, updateHoldSchema } from '@/types/hold.types';

export const PATCH = withApi(
  async (request, { userId, params }) => {
    const { id } = holdIdSchema.parse(await params);
    const data = updateHoldSchema.parse(await request.json());
    const hold = await holdService.update(userId, id, data);
    return success(hold);
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = holdIdSchema.parse(await params);
    await holdService.delete(userId, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
