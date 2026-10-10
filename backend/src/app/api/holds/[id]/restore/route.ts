import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { holdService } from '@/services/hold.service';
import { holdIdSchema } from '@/types/hold.types';

export const POST = withApi(
  async (_request, { userId, params }) => {
    const { id } = holdIdSchema.parse(await params);
    return success(await holdService.restore(userId, id));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
