import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billReadService } from '@/billai/service';

/** The phone turned AI off: queued bill reads stop (the cron would otherwise keep retrying them). */
export const POST = withApi(
  async (_request, { userId }) => success({ stopped: await billReadService.stopQueued(userId) }),
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
