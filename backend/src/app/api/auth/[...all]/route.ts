import { toNextJsHandler } from 'better-auth/next-js';
import { auth } from '@/config/auth';
import { config } from '@/config/env';
import { withNativeOrigin } from '@/lib/native-origin';

const handlers = toNextJsHandler(auth);

// The Android app is not a browser and sends no Origin; see withNativeOrigin.
const origin = new URL(config.apiUrl).origin;
export const GET = (request: Request) => handlers.GET(withNativeOrigin(request, origin));
export const POST = (request: Request) => handlers.POST(withNativeOrigin(request, origin));
