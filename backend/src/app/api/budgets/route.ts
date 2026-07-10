import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, created } from '@/lib/response';
import { budgetService } from '@/services/budget.service';
import { createBudgetSchema, budgetQuerySchema } from '@/types/budget.types';

export const POST = withApi(
  async (request, { userId }) => {
    const data = createBudgetSchema.parse(await request.json());
    const budget = await budgetService.create(userId, data);
    return created(budget);
  },
  { auth: 'required' }
);

export const GET = withApi(
  async (request, { userId }) => {
    const query = budgetQuerySchema.parse(Object.fromEntries(request.nextUrl.searchParams));
    const budgets = await budgetService.getAll(userId, query);
    return success(budgets);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
