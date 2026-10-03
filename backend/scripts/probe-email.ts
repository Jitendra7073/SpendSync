/**
 * Sends one test support email, in the exact format a real report uses, using backend/.env settings.
 * Run: npx vite-node scripts/probe-email.ts   (never prints secrets)
 */
import { config } from 'dotenv';
import { sendEmail, smtpFromEnv } from '../src/lib/email';
import { supportRecipients } from '../src/support/recipients';
import { composeReceiptEmail, } from '../src/support/templates';
import { composeTicketEmail, newTicketRef } from '../src/support/ticket';

config({ path: '.env' });

async function main() {
  const creds = smtpFromEnv();
  const to = supportRecipients();
  console.log(`SMTP: ${creds ? `${creds.host}:${creds.port} as ${creds.user}` : 'NOT CONFIGURED'}`);
  console.log(`From: ${creds?.from ?? creds?.user ?? '-'}`);
  console.log(`To:   ${to.join(', ') || 'NOT SET (SUPPORT_EMAIL_TO)'}`);

  const mail = composeTicketEmail({
    ref: newTicketRef(),
    category: 'assistant',
    message: 'TEST REPORT: this is a test of the support email, sent from the setup script.',
    context: { appVersion: 'test', device: 'script', os: 'n/a', language: 'English', screen: 'home', chat: [{ role: 'user', text: 'mene Uttam ko 200 diye' }] },
    user: { id: 'test-user', name: 'Asha Sharma', email: 'asha.test@example.com' },
    createdAt: new Date(),
  });
  const result = await sendEmail({ to, ...mail, replyTo: 'asha.test@example.com', fromName: 'Asha Sharma (SpendSync user)' });
  console.log(result.ok ? `RESULT: SENT (${result.messageId})` : `RESULT: FAILED -> ${result.reason} ${result.error ?? ''}`);
  console.log(`Subject: ${mail.subject}`);

  // The confirmation the person who sent the report receives (here sent to the SMTP account as a stand-in).
  const receipt = composeReceiptEmail({
    ref: 'SS-TEST22', category: 'assistant', message: 'TEST REPORT: this is a test of the support email, sent from the setup script.',
    context: { language: 'English' }, user: { name: 'Asha Sharma', email: creds?.user }, createdAt: new Date(),
  });
  const r2 = await sendEmail({ to: creds?.user ?? '', ...receipt, replyTo: to[0], fromName: 'SpendSync Support' });
  console.log(r2.ok ? `RECEIPT: SENT to ${creds?.user}` : `RECEIPT: FAILED -> ${r2.reason} ${r2.error ?? ''}`);
  console.log(`Subject: ${receipt.subject}`);
}

void main();
