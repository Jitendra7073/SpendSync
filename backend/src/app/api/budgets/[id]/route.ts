import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, noContent } from '@/lib/response';
import { budgetService } from '@/services/budget.service';
import { budgetIdSchema, updateBudgetSchema } from '@/types/budget.types';

export const GET = withApi(
  async (_request, { userId, params }) => {
    const { id } = budgetIdSchema.parse(await params);
    const budget = await budgetService.getById(userId, id);
    return success(budget);
  },
  { auth: 'required' }
);

export const PATCH = withApi(
  async (request, { userId, params }) => {
    const { id } = budgetIdSchema.parse(await params);
    const data = updateBudgetSchema.parse(await request.json());
    const budget = await budgetService.update(userId, id, data);
    return success(budget);
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = budgetIdSchema.parse(await params);
    await budgetService.delete(userId, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
