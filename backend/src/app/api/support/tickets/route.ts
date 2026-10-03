import { withApi, corsPreflight } from '@/lib/api-handler';
import { created, success } from '@/lib/response';
import { createTicket, listTickets } from '@/support/service';
import { createTicketSchema } from '@/support/ticket';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 30;

/** POST /api/support/tickets: send a report to the support team (saved first, then emailed). */
export const POST = withApi(
  async (request, { userId }) => {
    const body = createTicketSchema.parse(await request.json());
    return created(await createTicket(userId, body));
  },
  { auth: 'required' },
);

/** GET /api/support/tickets: the user's own reports and their status. */
export const GET = withApi(async (_request, { userId }) => success(await listTickets(userId)), { auth: 'required' });

export { corsPreflight as OPTIONS };
