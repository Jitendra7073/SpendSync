import { NextRequest, NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { auth } from '@/config/auth';
import { config } from '@/config/env';
import { ApiError, UnauthorizedError } from '@/utils/errors';
import { logger } from '@/utils/logger';
import { errorResponse } from '@/lib/response';

/**
 * Replaces middleware/rateLimiter.middleware.ts's express-rate-limit usage —
 * same in-memory, single-process sliding-window semantics, same config
 * source (config.rateLimit.windowMs/maxRequests).
 */
const buckets = new Map<string, { count: number; resetAt: number }>();

function checkRateLimit(request: NextRequest): void {
  const ip =
    request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    request.headers.get('x-real-ip') ||
    'unknown';
  const now = Date.now();
  const bucket = buckets.get(ip);

  if (!bucket || now >= bucket.resetAt) {
    buckets.set(ip, { count: 1, resetAt: now + config.rateLimit.windowMs });
    return;
  }

  bucket.count += 1;
  if (bucket.count > config.rateLimit.maxRequests) {
    throw new ApiError(429, 'Too many requests, please try again later', 'RATE_LIMIT_EXCEEDED');
  }
}

/**
 * Replaces the CORS setup in server.ts. Credentials are allowed, so the
 * origin must be reflected from the allow-list rather than using "*".
 */
function corsHeaders(request: NextRequest): Record<string, string> {
  const headers: Record<string, string> = {
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type,Authorization',
    'Access-Control-Allow-Credentials': 'true',
  };
  const origin = request.headers.get('origin');
  if (origin && config.cors.allowedOrigins.includes(origin)) {
    headers['Access-Control-Allow-Origin'] = origin;
  }
  return headers;
}

function applyCors(request: NextRequest, response: NextResponse): NextResponse {
  for (const [key, value] of Object.entries(corsHeaders(request))) {
    response.headers.set(key, value);
  }
  return response;
}

/** Shared `OPTIONS` export for every route file: `export { corsPreflight as OPTIONS }`. */
export function corsPreflight(request: NextRequest): NextResponse {
  return new NextResponse(null, { status: 204, headers: corsHeaders(request) });
}

/** Replaces middleware/error.middleware.ts's errorHandler — centralized here instead. */
function toErrorResponse(err: unknown, request: NextRequest): NextResponse {
  logger.error('API error', {
    message: err instanceof Error ? err.message : String(err),
    stack: err instanceof Error ? err.stack : undefined,
    path: request.nextUrl.pathname,
    method: request.method,
  });

  if (err instanceof ZodError) {
    return errorResponse(
      'Validation failed',
      422,
      'VALIDATION_ERROR',
      config.isDevelopment ? err.errors : undefined
    );
  }

  if (err instanceof ApiError) {
    return errorResponse(err.message, err.statusCode, err.code, err.details);
  }

  const message = err instanceof Error && config.isDevelopment ? err.message : 'Internal server error';
  const details = err instanceof Error && config.isDevelopment ? err.stack : undefined;
  return errorResponse(message, 500, 'INTERNAL_ERROR', details);
}

interface HandlerCtx {
  userId: string;
  // Left as `any` (rather than `Promise<Record<string,string>>`): Next.js's
  // generated route types (.next/types) check each route.ts's exported
  // handler against a route-specific `{ params: Promise<{id: string}> }`
  // shape, and a narrower declared type here would conflict with that
  // per-route inference.
  params?: any;
}

type ApiHandler = (request: NextRequest, ctx: HandlerCtx) => Promise<NextResponse>;

/**
 * Wraps every app/api/**\/route.ts export — replaces
 * middleware/{auth,rateLimiter,error}.middleware.ts and server.ts's CORS
 * setup in one place. `auth: 'required'` mirrors `authenticate`,
 * `auth: 'optional'` mirrors `optionalAuthenticate`; omitting it skips the
 * session lookup entirely.
 */
export function withApi(
  handler: ApiHandler,
  options: { auth?: 'required' | 'optional' } = {}
) {
  return async function (
    request: NextRequest,
    routeCtx: { params?: any } = {}
  ): Promise<NextResponse> {
    try {
      checkRateLimit(request);

      let userId: string | undefined;
      if (options.auth) {
        const session = await auth.api.getSession({ headers: request.headers });
        if (session?.user) {
          userId = session.user.id;
        } else if (options.auth === 'required') {
          throw new UnauthorizedError('Invalid or expired session');
        }
      }

      const response = await handler(request, { ...routeCtx, userId: userId as string });
      return applyCors(request, response);
    } catch (err) {
      return applyCors(request, toErrorResponse(err, request));
    }
  };
}
