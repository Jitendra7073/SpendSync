import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billService } from '@/bills/service';
import { billIdSchema } from '@/types/bill.types';


export const POST = withApi(
  async (_request, { userId, params }) => {
    const { id } = billIdSchema.parse(await params);
    return success(await billService.sign(userId, id));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
