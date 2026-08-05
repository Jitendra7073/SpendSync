import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, created } from '@/lib/response';
import { holdService } from '@/services/hold.service';
import { createHoldSchema, holdQuerySchema } from '@/types/hold.types';

export const POST = withApi(
  async (request, { userId }) => {
    const body = await request.json();
    const data = createHoldSchema.parse(body);
    const hold = await holdService.create(userId, data);
    return created(hold);
  },
  { auth: 'required' }
);

export const GET = withApi(
  async (request, { userId }) => {
    const query = holdQuerySchema.parse(Object.fromEntries(request.nextUrl.searchParams));
    const holdsList = await holdService.getAll(userId, query);
    return success(holdsList);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
