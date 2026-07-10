import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, noContent } from '@/lib/response';
import { categoryService } from '@/services/category.service';
import { categoryIdSchema, updateCategorySchema } from '@/types/category.types';

export const GET = withApi(
  async (_request, { userId, params }) => {
    const { id } = categoryIdSchema.parse(await params);
    const category = await categoryService.getById(userId, id);
    return success(category);
  },
  { auth: 'required' }
);

export const PATCH = withApi(
  async (request, { userId, params }) => {
    const { id } = categoryIdSchema.parse(await params);
    const data = updateCategorySchema.parse(await request.json());
    const category = await categoryService.update(userId, id, data);
    return success(category);
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = categoryIdSchema.parse(await params);
    await categoryService.delete(userId, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
