import { z } from 'zod';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { verifyCode } from '@/password-reset/service';
import { ApiError } from '@/utils/errors';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 30;

const body = z.object({ email: z.string().trim().email().max(254), code: z.string().min(4).max(20) });

/** POST /api/password/verify: is the emailed code correct? Only then does the app ask for the new password. */
export const POST = withApi(async (request) => {
  const { email, code } = body.parse(await request.json());
  const outcome = await verifyCode(email, code);
  if (outcome === 'ok') return success({ valid: true });
  if (outcome === 'expired') throw new ApiError(400, 'That code has expired. Ask for a new one.', 'CODE_EXPIRED');
  if (outcome === 'locked') throw new ApiError(429, 'Too many wrong tries. Ask for a new code.', 'TOO_MANY_ATTEMPTS');
  throw new ApiError(400, 'That code is not correct.', 'INVALID_CODE');
});

export { corsPreflight as OPTIONS };
