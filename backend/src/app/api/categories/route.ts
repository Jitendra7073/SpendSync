import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, created } from '@/lib/response';
import { categoryService } from '@/services/category.service';
import { createCategorySchema } from '@/types/category.types';

export const POST = withApi(
  async (request, { userId }) => {
    const data = createCategorySchema.parse(await request.json());
    const category = await categoryService.create(userId, data);
    return created(category);
  },
  { auth: 'required' }
);

export const GET = withApi(
  async (_request, { userId }) => {
    const categories = await categoryService.getAll(userId);
    return success(categories);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
