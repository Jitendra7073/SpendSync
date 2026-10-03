import nodemailer from 'nodemailer';

/**
 * A small, generic email sender: give it SMTP credentials and a payload, it sends one email.
 * Credentials come from the environment by default (SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD,
 * EMAIL_FROM) but any caller may pass its own. It never throws: the result says what happened, so a
 * mail problem can never lose the thing that triggered it.
 */
export interface SmtpCredentials {
  host: string;
  port: number;
  user: string;
  password: string;
  /** "From" address, e.g. "SpendSync <no-reply@example.com>". Defaults to the SMTP user. */
  from?: string;
}

export interface EmailPayload {
  to: string | string[];
  subject: string;
  text: string;
  html?: string;
  replyTo?: string;
}

export type EmailResult = { ok: true; messageId?: string } | { ok: false; reason: 'not_configured' | 'failed'; error?: string };

type Env = Record<string, string | undefined>;

/** Null when the environment has no usable SMTP settings. */
export function smtpFromEnv(env: Env = process.env): SmtpCredentials | null {
  const host = env.SMTP_HOST?.trim();
  const user = env.SMTP_USER?.trim();
  const password = env.SMTP_PASSWORD;
  if (!host || !user || !password) return null;
  const port = Number(env.SMTP_PORT) || 587;
  return { host, port, user, password, from: env.EMAIL_FROM?.trim() || undefined };
}

type TransportFactory = typeof nodemailer.createTransport;

export async function sendEmail(
  payload: EmailPayload,
  creds: SmtpCredentials | null = smtpFromEnv(),
  createTransport: TransportFactory = nodemailer.createTransport,
): Promise<EmailResult> {
  if (!creds) return { ok: false, reason: 'not_configured' };
  const recipients = (Array.isArray(payload.to) ? payload.to : [payload.to]).map((a) => a.trim()).filter(Boolean);
  if (recipients.length === 0) return { ok: false, reason: 'not_configured', error: 'no recipient' };

  try {
    const transport = createTransport({
      host: creds.host,
      port: creds.port,
      secure: creds.port === 465, // 465 = TLS from the start; 587 upgrades with STARTTLS
      auth: { user: creds.user, pass: creds.password },
      // Serverless functions are short-lived: fail fast instead of hanging until the platform kills us.
      connectionTimeout: 8_000,
      greetingTimeout: 8_000,
      socketTimeout: 12_000,
    });
    const info = await transport.sendMail({
      from: creds.from ?? creds.user,
      to: recipients,
      subject: payload.subject,
      text: payload.text,
      html: payload.html,
      replyTo: payload.replyTo,
    });
    return { ok: true, messageId: info.messageId };
  } catch (err) {
    return { ok: false, reason: 'failed', error: (err instanceof Error ? err.message : String(err)).slice(0, 300) };
  }
}
