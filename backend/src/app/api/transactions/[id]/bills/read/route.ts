import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billReadService } from '@/billai/service';
import { transactionIdSchema } from '@/types/transaction.types';
import { runReadSchema } from '@/types/billai.types';

export const maxDuration = 60;

export const GET = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    return success(await billReadService.get(userId, id));
  },
  { auth: 'required' }
);

export const POST = withApi(
  async (request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    const body = runReadSchema.parse(await request.json());
    return success(await billReadService.run(userId, id, { consent: body.aiConsent, force: body.force, prefs: body.prefs }));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
