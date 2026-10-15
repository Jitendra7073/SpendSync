import { after } from 'next/server';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billService } from '@/bills/service';
import { billIdSchema, confirmBillSchema } from '@/types/bill.types';
import { NotFoundError } from '@/utils/errors';
import { billReadService } from '@/billai/service';

// Leaves time for the bill read scheduled with after().
export const maxDuration = 60;

export const POST = withApi(
  async (request, { userId, params }) => {
    const { id } = billIdSchema.parse(await params);
    const resp = confirmBillSchema.parse(await request.json());
    const view = await billService.confirm({ userId, billId: id }, resp, false);
    if (!view) throw new NotFoundError('Bill not found');
    if (resp.aiConsent) {
      // Read the bill after the response is sent; run() skips if this exact set of pages was already read.
      after(() => billReadService.run(userId, view.transactionId, { consent: true }).then(() => undefined, () => undefined));
    }
    return success(view);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
