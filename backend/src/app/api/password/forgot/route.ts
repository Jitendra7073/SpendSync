import { z } from 'zod';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { requestReset } from '@/password-reset/service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 30;

const body = z.object({ email: z.string().trim().email().max(254), language: z.string().max(20).optional() });

/** POST /api/password/forgot: emails a 6-character reset code. The answer is always the same, so it cannot reveal who has an account. */
export const POST = withApi(async (request) => {
  const { email, language } = body.parse(await request.json());
  await requestReset(email, language);
  return success({ sent: true });
});

export { corsPreflight as OPTIONS };
