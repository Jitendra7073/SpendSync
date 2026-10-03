/** Sends a sample password-reset email (fake code) to the SMTP account itself. Run: npx vite-node scripts/probe-reset-email.ts */
import { config } from 'dotenv';
import { sendEmail, smtpFromEnv } from '../src/lib/email';
import { generateResetCode } from '../src/password-reset/code';
import { composeResetEmail } from '../src/password-reset/email';

config({ path: '.env' });

async function main() {
  const creds = smtpFromEnv();
  if (!creds) return console.log('SMTP not configured');
  const mail = composeResetEmail({ code: generateResetCode(), name: 'Asha Sharma', language: 'English' });
  const r = await sendEmail({ to: creds.user, ...mail, fromName: 'SpendSync' });
  console.log(r.ok ? `SENT to ${creds.user}: ${mail.subject}` : `FAILED ${r.reason} ${r.error ?? ''}`);
}

void main();
