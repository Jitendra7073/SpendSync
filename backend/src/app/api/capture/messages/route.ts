import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, created } from '@/lib/response';
import { ingest, list, removeAll } from '@/capture/service';
import { ingestSchema, listQuerySchema } from '@/types/capture.types';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/** POST /api/capture/messages: one bank SMS / email / UPI alert → added, duplicate, review or ignored. */
export const POST = withApi(
  async (request, { userId }) => created(await ingest(userId, ingestSchema.parse(await request.json()))),
  { auth: 'required' },
);

/** GET /api/capture/messages?status=&transactionId=&page=: the user's captured messages, newest first. */
export const GET = withApi(
  async (request, { userId }) => {
    const { rows, meta } = await list(userId, listQuerySchema.parse(Object.fromEntries(request.nextUrl.searchParams)));
    return success(rows, 200, meta);
  },
  { auth: 'required' },
);

/** DELETE /api/capture/messages: delete every saved copy (transactions stay). */
export const DELETE = withApi(async (_request, { userId }) => success(await removeAll(userId)), { auth: 'required' });

export { corsPreflight as OPTIONS };
