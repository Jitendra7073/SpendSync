import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { transactionService } from '@/services/transaction.service';
import { transactionIdSchema } from '@/types/transaction.types';

export const POST = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    return success(await transactionService.restore(userId, id));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
