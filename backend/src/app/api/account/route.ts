import { eq } from 'drizzle-orm';
import { db } from '@/db/index';
import { user } from '@/db/schema/index';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { enqueuePurges, runPurgesInline } from '@/bills/purge';
import { userPrefix } from '@/bills/rules';

/**
 * Permanently deletes the signed-in user. Sessions, transactions, budgets, categories,
 * holds, bills and settings all reference `user.id` with ON DELETE CASCADE, so one delete clears
 * everything; bill files leave Cloudinary via the purge outbox.
 */
export const DELETE = withApi(
  async (_request, { userId }) => {
    // Record the Cloudinary folder first: the cascade below removes every bills row.
    const purgeIds = await db.transaction(async (tx) => {
      const ids = await enqueuePurges(tx, [userPrefix(userId)], 'prefix');
      await tx.delete(user).where(eq(user.id, userId));
      return ids;
    });
    await runPurgesInline(purgeIds); // never throws; the cron retries
    return success({ deleted: true });
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
