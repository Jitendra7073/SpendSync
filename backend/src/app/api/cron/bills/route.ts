import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { config } from '@/config/env';
import { runBillsCron } from '@/bills/cron';
import { ApiError, UnauthorizedError } from '@/utils/errors';

export const maxDuration = 60;

/** Vercel Cron calls this daily with `Authorization: Bearer ${CRON_SECRET}`. */
export const GET = withApi(
  async (request) => {
    const secret = config.bills.cronSecret;
    if (!secret) throw new ApiError(503, 'CRON_SECRET is not set', 'CRON_NOT_CONFIGURED');
    if (request.headers.get('authorization') !== `Bearer ${secret}`) throw new UnauthorizedError('Bad cron secret');
    return success(await runBillsCron());
  },
  { rateLimit: false }
);

export { corsPreflight as OPTIONS };
