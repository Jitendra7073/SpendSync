import { eq } from 'drizzle-orm';
import { db } from '@/db/index';
import { user } from '@/db/schema/index';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';

/**
 * Permanently deletes the signed-in user. Sessions, transactions, budgets, categories,
 * holds and settings all reference `user.id` with ON DELETE CASCADE, so one delete clears
 * everything.
 */
export const DELETE = withApi(
  async (_request, { userId }) => {
    await db.delete(user).where(eq(user.id, userId));
    return success({ deleted: true });
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
