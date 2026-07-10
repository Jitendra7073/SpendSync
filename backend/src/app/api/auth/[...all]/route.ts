import { toNextJsHandler } from 'better-auth/next-js';
import { auth } from '@/config/auth';

export const { GET, POST } = toNextJsHandler(auth);
