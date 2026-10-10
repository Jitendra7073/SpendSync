import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { db } from '@/db/index';
import { billService, requireCloudinary } from '@/bills/service';
import { verifyWebhook } from '@/bills/cloudinary';
import { enqueuePurges, runPurges } from '@/bills/purge';
import { BILLS_ROOT } from '@/bills/rules';
import { webhookUploadSchema } from '@/types/bill.types';
import { UnauthorizedError } from '@/utils/errors';

/** Cloudinary's upload notification: the second, phone-independent way a bill gets confirmed. */
export const POST = withApi(
  async (request) => {
    const c = requireCloudinary();
    const body = await request.text();
    const ok = verifyWebhook(c, body, request.headers.get('x-cld-timestamp'), request.headers.get('x-cld-signature'), Math.floor(Date.now() / 1000));
    if (!ok) throw new UnauthorizedError('Invalid notification signature');
    const n = JSON.parse(body);
    if (n?.notification_type !== 'upload') return success({ ignored: true });
    const parsed = webhookUploadSchema.safeParse(n);
    if (!parsed.success) return success({ ignored: true });
    const view = await billService.confirm({ publicId: parsed.data.public_id }, parsed.data, true);
    if (!view && parsed.data.public_id.startsWith(BILLS_ROOT)) {
      // A file in our folder with no bill row is not ours to keep.
      const ids = await db.transaction((tx) => enqueuePurges(tx, [parsed.data.public_id]));
      await runPurges({ ids });
    }
    return success({ ok: true });
  },
  { rateLimit: false }
);

export { corsPreflight as OPTIONS };
