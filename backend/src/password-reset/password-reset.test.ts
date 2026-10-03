import { hashPassword, verifyPassword } from 'better-auth/crypto';
import { describe, expect, it } from 'vitest';
import { CODE_LENGTH, MAX_ATTEMPTS, generateResetCode, hashCode, judgeAttempt, normalizeCode, type ResetRecord } from './code';
import { composeResetEmail } from './email';

const SECRET = 's'.repeat(40);
const now = new Date('2026-10-03T10:00:00Z');
const later = (min: number) => new Date(now.getTime() + min * 60_000);

function record(code: string, over: Partial<ResetRecord> = {}): ResetRecord {
  return { codeHash: hashCode(code, 'a@x.com', SECRET), attempts: 0, expiresAt: later(15), usedAt: null, ...over };
}
const judge = (rec: ResetRecord | null, code: string) => judgeAttempt(rec, { code, email: 'a@x.com', secret: SECRET, now });

describe('reset code', () => {
  it('is six characters with at least one letter and one digit, and never looks ambiguous', () => {
    for (let i = 0; i < 300; i++) {
      const c = generateResetCode();
      expect(c).toHaveLength(CODE_LENGTH);
      expect(c).toMatch(/^[A-HJ-NP-Z2-9]{6}$/);
      expect(c).toMatch(/[A-Z]/);
      expect(c).toMatch(/[0-9]/);
    }
  });

  it('rejects an all-letter draw and tries again', () => {
    const draws = [0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 30]; // first six are letters, then one with a digit (index 30 = digit)
    let i = 0;
    const code = generateResetCode(() => draws[i++]);
    expect(code).toMatch(/[0-9]/);
    expect(code).toMatch(/[A-Z]/);
  });

  it('accepts what people really type: lower case, spaces, dashes', () => {
    expect(normalizeCode(' k7m-2qx ')).toBe('K7M2QX');
    expect(hashCode('k7m 2qx', 'A@X.com', SECRET)).toBe(hashCode('K7M2QX', 'a@x.com', SECRET));
  });
});

describe('judging an attempt', () => {
  it('accepts the right code once', () => {
    expect(judge(record('K7M2QX'), 'k7m2qx')).toBe('ok');
    expect(judge(record('K7M2QX', { usedAt: now }), 'K7M2QX')).toBe('none');
    expect(judge(null, 'K7M2QX')).toBe('none');
  });

  it('rejects a wrong code, an expired code, and a code after too many wrong tries', () => {
    expect(judge(record('K7M2QX'), 'ZZZZZZ')).toBe('wrong');
    expect(judge(record('K7M2QX', { expiresAt: later(-1) }), 'K7M2QX')).toBe('expired');
    expect(judge(record('K7M2QX', { attempts: MAX_ATTEMPTS }), 'K7M2QX')).toBe('locked'); // even the right one
  });

  it('ties the code to the email and the server secret', () => {
    const rec = record('K7M2QX');
    expect(judgeAttempt(rec, { code: 'K7M2QX', email: 'other@x.com', secret: SECRET, now })).toBe('wrong');
    expect(judgeAttempt(rec, { code: 'K7M2QX', email: 'a@x.com', secret: 'x'.repeat(40), now })).toBe('wrong');
  });
});

describe('new password', () => {
  it('is hashed in the format Better Auth signs in with', async () => {
    const hash = await hashPassword('Brand-new-pass1');
    expect(hash).not.toContain('Brand-new-pass1');
    expect(await verifyPassword({ hash, password: 'Brand-new-pass1' })).toBe(true);
    expect(await verifyPassword({ hash, password: 'wrong-pass' })).toBe(false);
  });
});

describe('reset email', () => {
  it('shows the code big, the expiry and a do-not-share warning, in the user language', () => {
    const en = composeResetEmail({ code: 'K7M2QX', name: 'Asha Sharma' });
    expect(en.text).toContain('K7M2QX');
    expect(en.text).toContain('Hi Asha,');
    expect(en.text).toContain('15 minutes');
    expect(en.html).toContain('K7M2QX');
    expect(composeResetEmail({ code: 'K7M2QX', language: 'Hindi' }).subject).toContain('पासवर्ड');
    expect(composeResetEmail({ code: 'K7M2QX', language: 'Nope' }).subject).toContain('password reset');
  });
});
