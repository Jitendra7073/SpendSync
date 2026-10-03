import { z } from 'zod';

export const TICKET_CATEGORIES = ['bug', 'wrong_data', 'assistant', 'account', 'feature', 'other'] as const;
export type TicketCategory = (typeof TICKET_CATEGORIES)[number];

const chatLine = z.object({ role: z.enum(['user', 'assistant']), text: z.string().max(600) });

export const createTicketSchema = z.object({
  category: z.enum(TICKET_CATEGORIES),
  message: z.string().trim().min(5).max(2000),
  context: z
    .object({
      appVersion: z.string().max(40).optional(),
      device: z.string().max(80).optional(),
      os: z.string().max(40).optional(),
      language: z.string().max(20).optional(),
      screen: z.string().max(40).optional(),
      /** Recent assistant chat. Only present when the user ticked "include my chat". */
      chat: z.array(chatLine).max(10).optional(),
    })
    .default({}),
});

export type CreateTicketInput = z.infer<typeof createTicketSchema>;

const CATEGORY_LABEL: Record<TicketCategory, string> = {
  bug: 'Bug / something broke',
  wrong_data: 'Wrong numbers or missing data',
  assistant: 'Assistant misbehaved',
  account: 'Account or sign-in',
  feature: 'Idea or request',
  other: 'Other',
};

/** Short, readable reference the user can quote: SS-7K3QX2. No look-alike characters. */
export function newTicketRef(rand: () => number = Math.random): string {
  const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  let out = '';
  for (let i = 0; i < 6; i++) out += alphabet[Math.floor(rand() * alphabet.length)];
  return `SS-${out}`;
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export interface TicketEmailInput {
  ref: string;
  category: TicketCategory;
  message: string;
  context: CreateTicketInput['context'];
  user: { id: string; name?: string | null; email?: string | null };
  createdAt: Date;
}

/** Everything a human needs to act on the report, as plain text and HTML. */
export function composeTicketEmail(t: TicketEmailInput): { subject: string; text: string; html: string } {
  const c = t.context;
  const facts: Array<[string, string]> = [
    ['Reference', t.ref],
    ['Type', CATEGORY_LABEL[t.category]],
    ['From', `${t.user.name ?? 'Unknown'} <${t.user.email ?? 'no email'}>`],
    ['User id', t.user.id],
    ['Sent', t.createdAt.toISOString()],
    ['App version', c.appVersion ?? '-'],
    ['Device', [c.device, c.os].filter(Boolean).join(' / ') || '-'],
    ['Language', c.language ?? '-'],
    ['Screen', c.screen ?? '-'],
  ];
  const chat = c.chat ?? [];
  const subject = `[SpendSync ${t.ref}] ${CATEGORY_LABEL[t.category]}: ${t.message.replace(/\s+/g, ' ').slice(0, 60)}`;

  const text = [
    ...facts.map(([k, v]) => `${k}: ${v}`),
    '',
    'What the user wrote:',
    t.message,
    ...(chat.length ? ['', 'Recent assistant chat (shared by the user):', ...chat.map((l) => `${l.role === 'user' ? 'User' : 'Assistant'}: ${l.text}`)] : []),
  ].join('\n');

  const html =
    `<table cellpadding="6" style="border-collapse:collapse;font-family:sans-serif;font-size:14px">` +
    facts.map(([k, v]) => `<tr><td style="color:#555">${esc(k)}</td><td><b>${esc(v)}</b></td></tr>`).join('') +
    `</table><h3 style="font-family:sans-serif">What the user wrote</h3><p style="font-family:sans-serif;white-space:pre-wrap">${esc(t.message)}</p>` +
    (chat.length
      ? `<h3 style="font-family:sans-serif">Recent assistant chat (shared by the user)</h3>` +
        chat.map((l) => `<p style="font-family:sans-serif;margin:4px 0"><b>${l.role === 'user' ? 'User' : 'Assistant'}:</b> ${esc(l.text)}</p>`).join('')
      : '');

  return { subject, text, html };
}
