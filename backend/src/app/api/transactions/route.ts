import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, created } from '@/lib/response';
import { transactionService } from '@/services/transaction.service';
import { createTransactionSchema, transactionQuerySchema } from '@/types/transaction.types';

export const POST = withApi(
  async (request, { userId }) => {
    const body = await request.json();
    const data = createTransactionSchema.parse(body);
    const transaction = await transactionService.create(userId, data);
    return created(transaction);
  },
  { auth: 'required' }
);

export const GET = withApi(
  async (request, { userId }) => {
    const query = transactionQuerySchema.parse(
      Object.fromEntries(request.nextUrl.searchParams)
    );
    const result = await transactionService.getAll(userId, query);
    return success(result.transactions, 200, result.meta);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
