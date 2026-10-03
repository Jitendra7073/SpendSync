/**
 * Better Auth rejects a POST that carries a cookie but no Origin ("Missing or null Origin"). A browser always
 * sends an Origin, so only a native client (our Android app, which has no origin) can be missing it. For
 * those, and only when BOTH Origin and Referer are absent, we fill in NATIVE_ORIGIN. A browser-based
 * CSRF attempt always has an Origin or Referer, so it is still checked normally; "null" is never rewritten.
 */
/**
 * The origin stamped on native-app requests. It is on the trusted list in `config/auth.ts`. `.invalid` is a reserved
 * top-level domain nobody can own, so no website can ever send this origin.
 */
export const NATIVE_ORIGIN = 'https://spend-sync-api.vercel.app';

export function withNativeOrigin(request: Request, apiOrigin: string = NATIVE_ORIGIN): Request {
  if (request.headers.get('origin') || request.headers.get('referer')) return request;
  const headers = new Headers(request.headers);
  headers.set('origin', apiOrigin);
  return new Request(request, { headers });
}
