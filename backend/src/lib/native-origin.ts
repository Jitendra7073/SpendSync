/**
 * Better Auth rejects a POST that carries a cookie but no Origin ("Missing or null Origin"). A browser always
 * sends an Origin, so only a native client (our Android app, which has no origin) can be missing it. For
 * those, and only when BOTH Origin and Referer are absent, we stamp a fixed origin that is on the trusted
 * list. A browser-based CSRF attempt always has an Origin or Referer, so it is still checked normally;
 * "null" is never rewritten.
 */

/**
 * The origin stamped on native-app requests. It is on the trusted list in `config/auth.ts`. `.invalid` is a reserved
 * top-level domain nobody can own, so no website can ever send this origin.
 */
export const NATIVE_ORIGIN = 'https://native.spendsync.invalid';

/**
 * The header is set IN PLACE. Cloning with `new Request(request, ...)` breaks inside Next.js ("Cannot read private
 * member #state"): the incoming request is a different Request class from the global one. If the headers turn
 * out to be read-only, a fresh request is built from plain fields (never from the foreign object itself).
 */
export function withNativeOrigin(request: Request, origin: string = NATIVE_ORIGIN): Request {
  if (request.headers.get('origin') || request.headers.get('referer')) return request;

  try {
    request.headers.set('origin', origin);
    if (request.headers.get('origin') === origin) return request;
  } catch {
    /* read-only headers: fall through */
  }

  const headers = new Headers();
  request.headers.forEach((value, key) => headers.set(key, value));
  headers.set('origin', origin);
  const init: RequestInit & { duplex?: 'half' } = { method: request.method, headers };
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = request.body;
    init.duplex = 'half';
  }
  return new Request(request.url, init);
}
