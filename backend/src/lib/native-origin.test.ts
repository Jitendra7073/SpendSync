import { describe, expect, it } from 'vitest';
import { withNativeOrigin } from './native-origin';

const req = (headers: Record<string, string> = {}) => new Request('https://api.example.com/api/auth/sign-in/email', { method: 'POST', body: '{"a":1}', headers });

describe('withNativeOrigin', () => {
  it('adds our own origin when a native client sends neither Origin nor Referer, keeping body and cookie', async () => {
    const out = withNativeOrigin(req({ cookie: 'x=1' }), 'https://api.example.com');
    expect(out.headers.get('origin')).toBe('https://api.example.com');
    expect(out.headers.get('cookie')).toBe('x=1');
    expect(await out.text()).toBe('{"a":1}');
  });

  it('never touches a request that has an Origin or Referer, including "null" (browser sandboxes)', () => {
    for (const h of [{ origin: 'https://evil.example' }, { origin: 'null' }, { referer: 'https://evil.example/page' }]) {
      const r = req(h);
      expect(withNativeOrigin(r, 'https://api.example.com')).toBe(r);
    }
  });
});
