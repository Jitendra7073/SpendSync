import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { categoryService } from '@/services/category.service';

export const POST = withApi(
  async (request, { userId }) => {
    const { merchant } = await request.json();
    const category = await categoryService.suggestCategory(userId, merchant);
    return success({ merchant, suggestedCategory: category });
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
