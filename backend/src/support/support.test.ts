import { describe, expect, it, vi } from 'vitest';

vi.stubEnv('DATABASE_URL', 'postgres://user:pass@localhost:5432/test');
vi.stubEnv('AUTH_SECRET', 'x'.repeat(40));
vi.mock('../db/index', () => ({ db: {} }));

import { hintsFromFeedback } from '../assistant/feedback';
import { sendEmail, smtpFromEnv } from '../lib/email';
import { supportRecipients } from './recipients';
import { composeReceiptEmail } from './templates';
import { composeTicketEmail, createTicketSchema, newTicketRef } from './ticket';

const creds = { host: 'smtp.test', port: 587, user: 'me@test.com', password: 'pw', from: 'SpendSync <me@test.com>' };

describe('email module', () => {
  it('reads credentials from the environment and needs all of host, user and password', () => {
    expect(smtpFromEnv({ SMTP_HOST: 'h', SMTP_USER: 'u', SMTP_PASSWORD: 'p', SMTP_PORT: '465', EMAIL_FROM: 'a@b.com' })).toEqual({ host: 'h', port: 465, user: 'u', password: 'p', from: 'a@b.com' });
    expect(smtpFromEnv({ SMTP_HOST: 'h', SMTP_USER: 'u' })).toBeNull();
  });

  it('sends one email through the given transport with TLS only on 465', async () => {
    const sendMail = vi.fn().mockResolvedValue({ messageId: 'm1' });
    const factory = vi.fn().mockReturnValue({ sendMail });
    const r = await sendEmail({ to: ['a@x.com', ' '], subject: 'S', text: 'T', replyTo: 'u@x.com' }, creds, factory as never);
    expect(r).toEqual({ ok: true, messageId: 'm1' });
    expect(factory.mock.calls[0][0]).toMatchObject({ host: 'smtp.test', secure: false, auth: { user: 'me@test.com', pass: 'pw' } });
    expect(sendMail).toHaveBeenCalledWith(expect.objectContaining({ from: 'SpendSync <me@test.com>', to: ['a@x.com'], replyTo: 'u@x.com' }));
    await sendEmail({ to: 'a@x.com', subject: 'S', text: 'T' }, { ...creds, port: 465 }, factory as never);
    expect(factory.mock.calls[1][0].secure).toBe(true);
  });

  it('never throws: reports not_configured and failures', async () => {
    expect(await sendEmail({ to: 'a@x.com', subject: 'S', text: 'T' }, null)).toEqual({ ok: false, reason: 'not_configured' });
    expect(await sendEmail({ to: [], subject: 'S', text: 'T' }, creds)).toMatchObject({ ok: false, reason: 'not_configured' });
    const boom = vi.fn().mockReturnValue({ sendMail: vi.fn().mockRejectedValue(new Error('auth failed')) });
    expect(await sendEmail({ to: 'a@x.com', subject: 'S', text: 'T' }, creds, boom as never)).toEqual({ ok: false, reason: 'failed', error: 'auth failed' });
  });
});

describe('where reports go', () => {
  it('comes only from SUPPORT_EMAIL_TO: nothing hardcoded, bad entries ignored, empty means not configured', () => {
    expect(supportRecipients({})).toEqual([]);
    expect(supportRecipients({ SUPPORT_EMAIL_TO: ' a@x.com , b@x.com ' })).toEqual(['a@x.com', 'b@x.com']);
    expect(supportRecipients({ SUPPORT_EMAIL_TO: 'not-an-email, ok@x.com,  ' })).toEqual(['ok@x.com']);
  });

  it('shows the user as the sender name (address stays the SMTP account) and keeps Reply-To', async () => {
    const sendMail = vi.fn().mockResolvedValue({ messageId: 'm' });
    await sendEmail({ to: 'team@x.com', subject: 'S', text: 'T', fromName: 'Asha "Boss" <x@y.com>\nBcc: evil', replyTo: 'asha@x.com' }, creds, (() => ({ sendMail })) as never);
    const sent = sendMail.mock.calls[0][0];
    expect(sent.from).toEqual({ name: 'Asha Boss x@y.com Bcc: evil', address: 'me@test.com' });
    expect(sent.replyTo).toBe('asha@x.com');
  });
});

describe('support ticket', () => {
  it('validates the report', () => {
    expect(createTicketSchema.safeParse({ category: 'bug', message: 'App crashes when I add an expense' }).success).toBe(true);
    expect(createTicketSchema.safeParse({ category: 'bug', message: 'no' }).success).toBe(false);
    expect(createTicketSchema.safeParse({ category: 'hack', message: 'something long enough' }).success).toBe(false);
  });

  it('makes short readable references', () => {
    expect(newTicketRef(() => 0)).toBe('SS-AAAAAA');
    expect(newTicketRef()).toMatch(/^SS-[A-Z2-9]{6}$/);
  });

  it('writes an email with everything a human needs, and escapes the user text', () => {
    const m = composeTicketEmail({
      ref: 'SS-ABC234',
      category: 'assistant',
      message: 'It said <b>sorry</b> instead of adding my expense',
      context: { appVersion: '1.2', device: 'Pixel 8', os: 'Android 15', language: 'Hindi', screen: 'home', chat: [{ role: 'user', text: 'mene 200 diye' }] },
      user: { id: 'u1', name: 'Asha', email: 'asha@x.com' },
      createdAt: new Date('2026-10-03T10:00:00Z'),
    });
    expect(m.subject).toContain('SS-ABC234');
    expect(m.subject).toContain('Asha <asha@x.com>');
    for (const part of ['asha@x.com', 'u1', 'Pixel 8', 'Hindi', 'mene 200 diye', 'Assistant misbehaved']) expect(m.text).toContain(part);
    expect(m.html).toContain('&lt;b&gt;sorry&lt;/b&gt;');
    expect(m.html).not.toContain('<b>sorry</b>');
  });
});

describe('confirmation email to the user', () => {
  const base = { ref: 'SS-ABC234', category: 'bug' as const, message: 'It crashed <twice>', user: { name: 'Asha Sharma', email: 'asha@x.com' }, createdAt: new Date('2026-10-03T10:00:00Z') };

  it('greets by first name, quotes the reference and the message, and escapes html', () => {
    const m = composeReceiptEmail({ ...base, context: {} });
    expect(m.subject).toContain('SS-ABC234');
    expect(m.text).toContain('Hi Asha,');
    expect(m.text).toContain('It crashed <twice>');
    expect(m.html).toContain('It crashed &lt;twice&gt;');
    expect(m.html).not.toContain('<twice>');
  });

  it('writes in the language the user uses in the app, falling back to English', () => {
    expect(composeReceiptEmail({ ...base, context: { language: 'Hindi' } }).text).toContain('नमस्ते Asha,');
    expect(composeReceiptEmail({ ...base, context: { language: 'German' } }).subject).toContain('Meldung');
    expect(composeReceiptEmail({ ...base, context: { language: 'Klingon' } }).text).toContain('Hi Asha,');
  });
});

describe('learning from feedback', () => {
  it('needs a repeated reason, or the latest one, before changing behaviour', () => {
    expect(hintsFromFeedback([{ rating: 'up', reasons: '' }, { rating: 'down', reasons: 'too_long' }])).toEqual([]);
    expect(hintsFromFeedback([{ rating: 'down', reasons: 'too_long' }, { rating: 'up', reasons: '' }]).join(' ')).toContain('shorter');
    const twice = hintsFromFeedback([{ rating: 'up', reasons: '' }, { rating: 'down', reasons: 'wrong_language' }, { rating: 'down', reasons: 'wrong_language,confusing' }]);
    expect(twice.join(' ')).toContain('language');
    expect(twice.join(' ')).not.toContain('simpler');
  });

  it('ignores unknown reasons and ratings of "up"', () => {
    expect(hintsFromFeedback([{ rating: 'down', reasons: 'made_up' }])).toEqual([]);
    expect(hintsFromFeedback([{ rating: 'up', reasons: 'too_long' }])).toEqual([]);
  });
});
