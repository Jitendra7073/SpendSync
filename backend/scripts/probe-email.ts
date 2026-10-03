/** Sends one test email using backend/.env settings. Run: npx vite-node scripts/probe-email.ts  (never prints secrets) */
import { config } from 'dotenv';
import { sendEmail, smtpFromEnv } from '../src/lib/email';

config({ path: '.env' });

async function main() {
  const to = (process.env.SUPPORT_EMAIL_TO ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  console.log('smtp configured:', smtpFromEnv() !== null, '| recipients:', to.length);
  const result = await sendEmail({
    to,
    subject: '[SpendSync] Test email from the support module',
    text: 'If you can read this, SpendSync can send support reports to this address.',
  });
  console.log(result.ok ? `SENT (${result.messageId})` : `FAILED: ${result.reason} ${result.error ?? ''}`);
}

void main();
