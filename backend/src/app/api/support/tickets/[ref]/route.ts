import { z } from 'zod';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { closeTicket } from '@/support/service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const bodySchema = z.object({ status: z.literal('closed') });

/** PATCH /api/support/tickets/SS-ABC123 {status:"closed"}: the user closes one of their own reports. */
export const PATCH = withApi(
  async (request, { userId, params }) => {
    const { ref } = await params;
    bodySchema.parse(await request.json());
    return success(await closeTicket(userId, String(ref)));
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
