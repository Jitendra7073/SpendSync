import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { autoClassifyService } from '@/planify/auto-classify.service';

export const POST = withApi(
  async (request, { userId }) => {
    // We allow the user to trigger their own auto-classification 
    // This could also be extended to run globally via a cron service by checking a secret key
    const result = await autoClassifyService.classifyForUser(userId);
    return success(result);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
