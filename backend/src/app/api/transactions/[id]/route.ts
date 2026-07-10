import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, noContent } from '@/lib/response';
import { transactionService } from '@/services/transaction.service';
import { transactionIdSchema, updateTransactionSchema } from '@/types/transaction.types';

export const GET = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    const transaction = await transactionService.getById(userId, id);
    return success(transaction);
  },
  { auth: 'required' }
);

export const PATCH = withApi(
  async (request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    const data = updateTransactionSchema.parse(await request.json());
    const transaction = await transactionService.update(userId, id, data);
    return success(transaction);
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    await transactionService.delete(userId, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
