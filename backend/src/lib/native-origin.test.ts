import { describe, expect, it } from 'vitest';
import { NATIVE_ORIGIN, withNativeOrigin } from './native-origin';

const req = (headers: Record<string, string> = {}) =>
  new Request('https://api.example.com/api/auth/sign-in/email', { method: 'POST', body: '{"a":1}', headers });

describe('withNativeOrigin', () => {
  it('stamps the native origin when a client sends neither Origin nor Referer, keeping body and cookie', async () => {
    const r = req({ cookie: 'x=1' });
    const out = withNativeOrigin(r);
    expect(out).toBe(r); // changed in place: no cloning of the request object
    expect(out.headers.get('origin')).toBe(NATIVE_ORIGIN);
    expect(NATIVE_ORIGIN).toMatch(/\.invalid$/);
    expect(out.headers.get('cookie')).toBe('x=1');
    expect(await out.text()).toBe('{"a":1}');
  });

  it('never touches a request that has an Origin or Referer, including "null" (browser sandboxes)', () => {
    for (const h of [{ origin: 'https://evil.example' }, { origin: 'null' }, { referer: 'https://evil.example/page' }]) {
      const r = req(h);
      expect(withNativeOrigin(r)).toBe(r);
      expect(r.headers.get('origin') ?? '').not.toBe(NATIVE_ORIGIN);
    }
  });

  it('falls back to a fresh request, built from plain fields, when the headers are read-only', async () => {
    const real = req({ cookie: 'x=1', 'content-type': 'application/json' });
    const readOnly = {
      url: real.url,
      method: real.method,
      body: real.body,
      headers: {
        get: (k: string) => real.headers.get(k),
        forEach: (cb: (v: string, k: string) => void) => real.headers.forEach(cb),
        set: () => {
          throw new TypeError('immutable');
        },
      },
    } as unknown as Request;
    const out = withNativeOrigin(readOnly);
    expect(out).not.toBe(readOnly);
    expect(out.headers.get('origin')).toBe(NATIVE_ORIGIN);
    expect(out.headers.get('cookie')).toBe('x=1');
    expect(await out.text()).toBe('{"a":1}');
  });
});
