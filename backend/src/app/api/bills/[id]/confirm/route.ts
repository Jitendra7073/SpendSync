import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billService } from '@/bills/service';
import { billIdSchema, confirmBillSchema } from '@/types/bill.types';
import { NotFoundError } from '@/utils/errors';


export const POST = withApi(
  async (request, { userId, params }) => {
    const { id } = billIdSchema.parse(await params);
    const resp = confirmBillSchema.parse(await request.json());
    const view = await billService.confirm({ userId, billId: id }, resp, false);
    if (!view) throw new NotFoundError('Bill not found');
    return success(view);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
