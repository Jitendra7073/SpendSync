import { withApi, corsPreflight } from '@/lib/api-handler';
import { created, success } from '@/lib/response';
import { billService } from '@/bills/service';
import { transactionIdSchema } from '@/types/transaction.types';
import { reserveBillSchema } from '@/types/bill.types';

export const GET = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    return success(await billService.list(userId, id));
  },
  { auth: 'required' }
);

export const POST = withApi(
  async (request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    const input = reserveBillSchema.parse(await request.json());
    return created(await billService.reserve(userId, id, input));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
