/**
 * End-to-end diagnosis of the whole auth flow against a RUNNING local server (default http://localhost:3113),
 * using throwaway accounts that are deleted at the end. Emails must be switched off on that server
 * (start it with SMTP_HOST= ) so no real mail is sent; reset codes are planted in the database with a known
 * value so the verify/reset endpoints can be exercised for real.
 *
 *   SMTP_HOST= RATE_LIMIT_MAX_REQUESTS=5000 npx next dev -p 3113
 *   npx vite-node scripts/diagnose-auth.ts
 */
import { config as loadEnv } from 'dotenv';

loadEnv({ path: '.env' });
const BASE = process.env.DIAG_BASE ?? 'http://localhost:3113';

type Res = { status: number; json: any; headers: Headers; ms: number };
const results: Array<{ group: string; name: string; ok: boolean; detail: string }> = [];
let group = '';

async function call(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<Res> {
  const t0 = Date.now();
  const r = await fetch(BASE + path, {
    method,
    headers: { ...(body !== undefined ? { 'content-type': 'application/json' } : {}), ...headers },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json: any = null;
  try { json = JSON.parse(text); } catch { json = text.slice(0, 120); }
  return { status: r.status, json, headers: r.headers, ms: Date.now() - t0 };
}
const post = (p: string, b: unknown, h?: Record<string, string>) => call('POST', p, b, h);
/** Better Auth sends { code }, our routes send { error: { code } }. */
const codeOf = (j: any): string | undefined => j?.code ?? j?.error?.code;
const bearer = (t: string) => ({ authorization: `Bearer ${t}` });

function check(name: string, ok: boolean, detail = '') {
  results.push({ group, name, ok, detail });
  console.log(`${ok ? '  PASS' : '  FAIL'}  ${name}${detail ? `  [${detail}]` : ''}`);
}
const section = (g: string) => { group = g; console.log(`\n== ${g}`); };

const pw = 'Probe-pass-12345';
const created: string[] = [];
const rand = () => `diag-${Date.now().toString(36)}${Math.floor(Math.random() * 1e5)}@example.com`;

async function newUser(password = pw) {
  const email = rand();
  const r = await post('/api/auth/sign-up/email', { email, password, name: 'Diag User' });
  created.push(email);
  return { email, token: r.headers.get('set-auth-token') ?? '', res: r };
}
async function signIn(email: string, password: string) {
  const r = await post('/api/auth/sign-in/email', { email, password });
  return { res: r, token: r.headers.get('set-auth-token') ?? '' };
}

async function main() {
  const { db } = await import('../src/db/index');
  const { passwordResets, user } = await import('../src/db/schema/index');
  const { and, desc, eq } = await import('drizzle-orm');
  const { hashCode } = await import('../src/password-reset/code');
  const secret = process.env.AUTH_SECRET!;
  const KNOWN = 'K7M2QX';

  const rowsFor = async (email: string) => {
    const [u] = await db.select({ id: user.id }).from(user).where(eq(user.email, email)).limit(1);
    if (!u) return [];
    return db.select().from(passwordResets).where(eq(passwordResets.userId, u.id)).orderBy(desc(passwordResets.createdAt));
  };
  const plant = async (email: string, code = KNOWN, patch: Record<string, unknown> = {}) => {
    const [row] = await rowsFor(email);
    await db.update(passwordResets).set({ codeHash: hashCode(code, email, secret), attempts: 0, usedAt: null, expiresAt: new Date(Date.now() + 600_000), ...patch }).where(eq(passwordResets.id, row.id));
  };

  // ── Sign up ───────────────────────────────────────────────────────────────
  section('Sign up');
  const a = await newUser();
  check('valid sign-up returns 200', a.res.status === 200, `HTTP ${a.res.status}`);
  check('sign-up returns a bearer token header (what the app saves)', a.token.length > 20);
  check('sign-up returns the user (id, email, name)', a.res.json?.user?.email === a.email && !!a.res.json?.user?.id);
  const dup = await post('/api/auth/sign-up/email', { email: a.email, password: pw, name: 'Dup' });
  check('duplicate email is rejected with USER_ALREADY_EXISTS', dup.status >= 400 && dup.status < 500 && /EXISTS/i.test(dup.json?.code ?? ''), `HTTP ${dup.status} ${dup.json?.code}`);
  const short = await post('/api/auth/sign-up/email', { email: rand(), password: 'short1', name: 'S' });
  check('too-short password is rejected', short.status >= 400 && short.status < 500, `HTTP ${short.status} ${short.json?.code}`);
  const badMail = await post('/api/auth/sign-up/email', { email: 'not-an-email', password: pw, name: 'S' });
  check('malformed email is rejected', badMail.status >= 400 && badMail.status < 500, `HTTP ${badMail.status}`);

  // ── Sign in ───────────────────────────────────────────────────────────────
  section('Sign in');
  const s1 = await signIn(a.email, pw);
  check('correct password signs in (200 + token)', s1.res.status === 200 && s1.token.length > 20);
  const wrong = await signIn(a.email, 'Wrong-pass-0000');
  check('wrong password -> 401 INVALID_EMAIL_OR_PASSWORD', wrong.res.status === 401 && wrong.res.json?.code === 'INVALID_EMAIL_OR_PASSWORD');
  const ghost = await signIn('nobody-' + rand(), pw);
  check('unknown email gives the SAME error (no account enumeration)', ghost.res.status === 401 && ghost.res.json?.code === 'INVALID_EMAIL_OR_PASSWORD');
  const emailCase = await signIn(a.email.toUpperCase(), pw);
  check('email is case-insensitive at sign-in', emailCase.res.status === 200, `HTTP ${emailCase.res.status}`);

  // ── Origin handling (the earlier login bug) ──────────────────────────────
  section('Origin / cookie handling');
  const stale = await post('/api/auth/sign-in/email', { email: a.email, password: pw }, { cookie: '__Secure-better-auth.session_token=stale.value' });
  check('app-style request with a STALE cookie and no Origin still signs in', stale.status === 200, `HTTP ${stale.status}`);
  const evil = await post('/api/auth/sign-in/email', { email: a.email, password: pw }, { cookie: 'a=b', origin: 'https://evil.example' });
  check('browser request from an untrusted origin is blocked (403)', evil.status === 403, `HTTP ${evil.status}`);
  const nul = await post('/api/auth/sign-in/email', { email: a.email, password: pw }, { cookie: 'a=b', origin: 'null' });
  check('"null" origin with a cookie is blocked (403)', nul.status === 403, `HTTP ${nul.status}`);

  // ── Session / protected API ──────────────────────────────────────────────
  section('Session and protected API');
  const sess = await call('GET', '/api/auth/get-session', undefined, bearer(s1.token));
  check('get-session with the bearer token returns the session', sess.status === 200 && sess.json?.session && sess.json?.user?.email === a.email);
  for (const p of ['/api/settings', '/api/transactions', '/api/dashboard/summary', '/api/budgets', '/api/holds', '/api/categories']) {
    const r = await call('GET', p, undefined, bearer(s1.token));
    check(`GET ${p} works with the token`, r.status === 200, `HTTP ${r.status}`);
  }
  const noAuth = await call('GET', '/api/settings');
  check('protected API without a token -> 401', noAuth.status === 401, `HTTP ${noAuth.status}`);
  const junk = await call('GET', '/api/settings', undefined, bearer('garbage.token'));
  check('protected API with a garbage token -> 401', junk.status === 401, `HTTP ${junk.status}`);
  const so = await post('/api/auth/sign-out', {}, bearer(s1.token));
  check('sign-out succeeds', so.status === 200, `HTTP ${so.status}`);
  const afterSo = await call('GET', '/api/settings', undefined, bearer(s1.token));
  check('the token stops working after sign-out', afterSo.status === 401, `HTTP ${afterSo.status}`);

  // ── Forgot password ──────────────────────────────────────────────────────
  section('Forgot password: request');
  const f = await newUser();
  const fKnown = await post('/api/password/forgot', { email: f.email, language: 'English' });
  const fUnknown = await post('/api/password/forgot', { email: rand(), language: 'English' });
  check('known email -> 200 { sent: true }', fKnown.status === 200 && fKnown.json?.data?.sent === true);
  check('unknown email -> identical answer (no account enumeration)', fUnknown.status === 200 && JSON.stringify(fUnknown.json?.data) === JSON.stringify(fKnown.json?.data));
  check('response times are similar for known/unknown email', Math.abs(fKnown.ms - fUnknown.ms) < 1500, `known ${fKnown.ms}ms, unknown ${fUnknown.ms}ms`);
  const badForgot = await post('/api/password/forgot', { email: 'nope' });
  check('malformed email -> 422 validation error', badForgot.status === 422, `HTTP ${badForgot.status}`);
  const rows1 = await rowsFor(f.email);
  check('a code row was created, expiring in about 15 minutes', rows1.length === 1 && Math.abs(rows1[0].expiresAt.getTime() - Date.now() - 900_000) < 60_000);
  check('only a hash is stored (64 hex chars), never the code', /^[0-9a-f]{64}$/.test(rows1[0]?.codeHash ?? ''));

  section('Forgot password: verify the code');
  await plant(f.email);
  const vWrong = await post('/api/password/verify', { email: f.email, code: 'ZZZZZZ' });
  check('wrong code -> 400 INVALID_CODE', vWrong.status === 400 && codeOf(vWrong.json) === 'INVALID_CODE');
  check('a wrong try is counted', (await rowsFor(f.email))[0].attempts === 1);
  const vOk = await post('/api/password/verify', { email: f.email, code: 'k7m 2qx' });
  check('right code (typed lower-case with a space) -> 200 { valid: true }', vOk.status === 200 && vOk.json?.data?.valid === true);
  const vAgain = await post('/api/password/verify', { email: f.email, code: KNOWN });
  check('verifying does not use the code up (it is still valid for the reset)', vAgain.status === 200);
  const vUnknown = await post('/api/password/verify', { email: rand(), code: KNOWN });
  check('verify for an unknown email -> same INVALID_CODE (no enumeration)', vUnknown.status === 400 && codeOf(vUnknown.json) === 'INVALID_CODE');

  section('Forgot password: lockout, expiry, replacement');
  const l = await newUser();
  await post('/api/password/forgot', { email: l.email });
  await plant(l.email);
  for (let i = 0; i < 5; i++) await post('/api/password/verify', { email: l.email, code: 'AAAAAA' });
  const locked = await post('/api/password/verify', { email: l.email, code: KNOWN });
  check('after 5 wrong tries even the right code is refused (429 TOO_MANY_ATTEMPTS)', locked.status === 429 && codeOf(locked.json) === 'TOO_MANY_ATTEMPTS', `HTTP ${locked.status} ${codeOf(locked.json)}`);
  await post('/api/password/forgot', { email: l.email });
  const rowsL = await rowsFor(l.email);
  check('asking for a new code cancels the locked one', rowsL.length === 2 && rowsL[1].usedAt !== null && rowsL[0].usedAt === null);
  await plant(l.email, KNOWN, { expiresAt: new Date(Date.now() - 1000) });
  const expired = await post('/api/password/verify', { email: l.email, code: KNOWN });
  check('an expired code -> 400 CODE_EXPIRED', expired.status === 400 && codeOf(expired.json) === 'CODE_EXPIRED', `${codeOf(expired.json)}`);

  section('Forgot password: set the new password');
  const r1 = await newUser();
  const oldToken = r1.token;
  await post('/api/password/forgot', { email: r1.email });
  await plant(r1.email);
  const weakPw = await post('/api/password/reset', { email: r1.email, code: KNOWN, newPassword: 'short' });
  check('a too-short new password -> 422', weakPw.status === 422, `HTTP ${weakPw.status}`);
  const badCodeReset = await post('/api/password/reset', { email: r1.email, code: 'ZZZZZZ', newPassword: 'Brand-new-pass-9' });
  check('reset with a wrong code is refused and changes nothing', badCodeReset.status === 400 && (await signIn(r1.email, pw)).res.status === 200);
  const done = await post('/api/password/reset', { email: r1.email, code: KNOWN, newPassword: 'Brand-new-pass-9' });
  check('reset with the right code -> 200', done.status === 200 && done.json?.data?.reset === true, `HTTP ${done.status}`);
  const reuse = await post('/api/password/reset', { email: r1.email, code: KNOWN, newPassword: 'Another-pass-77' });
  check('the code cannot be used twice', reuse.status === 400, `HTTP ${reuse.status}`);
  check('the OLD password no longer signs in', (await signIn(r1.email, pw)).res.status === 401);
  const newSign = await signIn(r1.email, 'Brand-new-pass-9');
  check('the NEW password signs in', newSign.res.status === 200 && newSign.token.length > 20);
  const oldSess = await call('GET', '/api/settings', undefined, bearer(oldToken));
  check('sessions from before the reset are signed out everywhere', oldSess.status === 401, `HTTP ${oldSess.status}`);
  const newSess = await call('GET', '/api/settings', undefined, bearer(newSign.token));
  check('the new session works', newSess.status === 200);

  section('Forgot password: abuse limits');
  const lim = await newUser();
  for (let i = 0; i < 5; i++) await post('/api/password/forgot', { email: lim.email });
  const limRows = await rowsFor(lim.email);
  check('only 3 codes are created per hour, extra requests still answer 200', limRows.length === 3, `${limRows.length} rows`);

  // ── Clean up ─────────────────────────────────────────────────────────────
  section('Clean-up');
  let deleted = 0;
  for (const email of created) {
    const pwds = [pw, 'Brand-new-pass-9'];
    for (const p of pwds) {
      const s = await signIn(email, p);
      if (s.token) {
        const d = await call('DELETE', '/api/account', undefined, bearer(s.token));
        if (d.status === 200) deleted++;
        break;
      }
    }
  }
  // anything left (e.g. never signed in) is removed directly
  for (const email of created) await db.delete(user).where(eq(user.email, email));
  const left = await db.select({ id: user.id }).from(user).where(and(eq(user.name, 'Diag User')));
  check(`all ${created.length} throwaway accounts removed`, left.length === 0, `${deleted} via API, rest direct`);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  if (failed.length) {
    console.log('FAILED:');
    for (const f2 of failed) console.log(`  - [${f2.group}] ${f2.name} ${f2.detail}`);
  }
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => { console.error('Diagnosis crashed:', e); process.exit(2); });
