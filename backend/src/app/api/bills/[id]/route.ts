import { withApi, corsPreflight } from '@/lib/api-handler';
import { noContent } from '@/lib/response';
import { billService } from '@/bills/service';
import { billIdSchema } from '@/types/bill.types';


export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = billIdSchema.parse(await params);
    await billService.softDelete(userId, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
