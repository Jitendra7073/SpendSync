import { withApi, corsPreflight } from '@/lib/api-handler';
import { noContent } from '@/lib/response';
import { trashService } from '@/trash/service';
import { trashTargetSchema } from '@/types/trash.types';

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { kind, id } = trashTargetSchema.parse(await params);
    await trashService.deleteForever(userId, kind, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
