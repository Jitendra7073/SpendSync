import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { trashService } from '@/trash/service';
import { trashQuerySchema } from '@/types/trash.types';

export const GET = withApi(
  async (request, { userId }) => {
    const { cursor, limit } = trashQuerySchema.parse(Object.fromEntries(request.nextUrl.searchParams));
    return success(await trashService.list(userId, cursor, limit));
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId }) => success(await trashService.empty(userId)),
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
