import { toNextJsHandler } from 'better-auth/next-js';
import { auth } from '@/config/auth';
import { withNativeOrigin } from '@/lib/native-origin';

const handlers = toNextJsHandler(auth);

// The Android app is not a browser and sends no Origin; see withNativeOrigin.
export const GET = (request: Request) => handlers.GET(withNativeOrigin(request));
export const POST = (request: Request) => handlers.POST(withNativeOrigin(request));
