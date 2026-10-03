import { after } from 'next/server';
import { z } from 'zod';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { requestReset } from '@/password-reset/service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 30;

const MIN_MS = 1500;

const body = z.object({ email: z.string().trim().email().max(254), language: z.string().max(20).optional() });

/** POST /api/password/forgot: emails a 6-character reset code. The answer is always the same, so it cannot reveal who has an account. */
export const POST = withApi(async (request) => {
  const { email, language } = body.parse(await request.json());
  const started = Date.now();
  const sendMail = await requestReset(email, language);
  if (sendMail) after(sendMail); // mail goes out after we have answered
  // Known and unknown emails take the same time, so timing cannot reveal who has an account.
  await new Promise((r) => setTimeout(r, Math.max(0, MIN_MS - (Date.now() - started))));
  return success({ sent: true });
});

export { corsPreflight as OPTIONS };
