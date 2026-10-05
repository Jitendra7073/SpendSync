import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { emailExport, exportEmailSchema } from '@/export/service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 30;

/** POST /api/export/email: email the signed-in user's own data (CSV or JSON) to their account address. */
export const POST = withApi(
  async (request, { userId }) => success(await emailExport(userId, exportEmailSchema.parse(await request.json()))),
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
