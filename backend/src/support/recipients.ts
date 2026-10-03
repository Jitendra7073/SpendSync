import { z } from 'zod';

const email = z.string().email();

/**
 * Who receives support reports: SUPPORT_EMAIL_TO from the environment (comma-separated). Nothing is
 * hardcoded; invalid entries are ignored, and an empty result means "email not configured" (the report
 * is still saved).
 */
export function supportRecipients(env: Record<string, string | undefined> = process.env): string[] {
  return (env.SUPPORT_EMAIL_TO ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => email.safeParse(s).success);
}
