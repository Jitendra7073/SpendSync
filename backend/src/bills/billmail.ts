export const MAX_EMAIL_BYTES = 15 * 1024 * 1024;
const DAILY = 20;
const MINUTE_MS = 60_000;

export const maskEmail = (e: string) => e.replace(/^(.).*(@.*)$/, '$1***$2');

export type MailFile = { filename: string; contentType: string; bytes: Buffer | null; label: string };

/** In order; stops adding at [max]. Files that failed to download (bytes null) are listed as not attached. */
export function packAttachments(files: MailFile[], max = MAX_EMAIL_BYTES) {
  const attached: MailFile[] = [];
  const notAttached: string[] = [];
  let used = 0;
  for (const f of files) {
    if (!f.bytes || used + f.bytes.length > max) {
      notAttached.push(f.label);
      continue;
    }
    attached.push(f);
    used += f.bytes.length;
  }
  return { attached, notAttached };
}

export type MailEntry = { date: string; merchant: string; amount: number; summary: string | null; files: number };

const inr = (n: number) => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);

export function composeBillsEmail(entries: MailEntry[], notAttached: string[], skipped: number) {
  const lines = entries.map((e) => `${e.date} · ${e.merchant} · ${inr(e.amount)}${e.files > 1 ? ` · ${e.files} files` : ''}${e.summary ? `\n  ${e.summary}` : ''}`);
  const extra = [
    ...(notAttached.length ? [`Not attached (too large or unavailable) — open SpendSync to view:\n${notAttached.map((n) => `  - ${n}`).join('\n')}`] : []),
    ...(skipped ? [`${skipped} bill${skipped === 1 ? ' was' : 's were'} no longer available.`] : []),
  ];
  const text = ['Here are the bills you asked for from SpendSync.', '', ...lines, '', ...extra].join('\n').trim();
  const html = `<p>Here are the bills you asked for from SpendSync.</p><ul>${entries
    .map((e) => `<li><strong>${esc(e.date)}</strong> · ${esc(e.merchant)} · ${esc(inr(e.amount))}${e.summary ? `<br><small>${esc(e.summary)}</small>` : ''}</li>`)
    .join('')}</ul>${extra.map((x) => `<p>${esc(x).replace(/\n/g, '<br>')}</p>`).join('')}`;
  return { subject: `Your bills from SpendSync (${entries.length})`, text, html };
}

/** Once a minute and 20 a day (UTC), per user. */
export function emailCooldown(row: { count: number; lastAt: Date } | undefined, now: Date): { ok: true } | { ok: false; reason: 'minute' | 'day'; waitSec: number } {
  if (!row) return { ok: true };
  if (row.count >= DAILY) {
    const midnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
    return { ok: false, reason: 'day', waitSec: Math.ceil((midnight - now.getTime()) / 1000) };
  }
  const since = now.getTime() - row.lastAt.getTime();
  if (since < MINUTE_MS) return { ok: false, reason: 'minute', waitSec: Math.ceil((MINUTE_MS - since) / 1000) };
  return { ok: true };
}
