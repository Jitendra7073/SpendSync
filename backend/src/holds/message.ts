import { z } from 'zod';

/**
 * Drafting the follow-up message for a hold ("Hi Asha, a reminder about the 1,500 you borrowed..."). The model only
 * WRITES words; the facts (who, how much, when) come from the hold and are checked in the draft, and the person's
 * phone number or email never leave the phone. The draft is shown to the user, who must confirm before anything is
 * opened in WhatsApp, SMS or email.
 */
export const messageRequestSchema = z.object({
  personName: z.string().trim().min(1).max(80),
  direction: z.enum(['owed_to_me', 'owed_by_me']),
  amount: z.number().positive().max(1_000_000_000),
  dueDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  overdueDays: z.number().int().min(0).max(3650).default(0),
  channel: z.enum(['whatsapp', 'sms', 'email']).default('whatsapp'),
  tone: z.enum(['gentle', 'friendly', 'firm']).default('friendly'),
  language: z.enum(['English', 'Hindi', 'Spanish', 'French', 'German']).default('English'),
  /** Extra detail from the user ("it was for the trip"), at most a couple of sentences. */
  context: z.string().trim().max(300).optional(),
  /** The draft the user rejected, so the next one is different. */
  previous: z.string().trim().max(800).optional(),
  userName: z.string().trim().max(60).optional(),
});
export type MessageRequest = z.infer<typeof messageRequestSchema>;

export function systemPrompt(r: MessageRequest): string {
  const limit = r.channel === 'sms' ? 300 : r.channel === 'email' ? 900 : 500;
  return [
    'You write a short personal message for the user of a money-tracking app to send to someone they know.',
    r.direction === 'owed_to_me'
      ? 'The other person owes the user money and the user wants to remind them politely.'
      : 'The user owes the other person money and wants to tell them when it will be returned (or say thank you and that it is on its way).',
    `Tone: ${r.tone} (gentle = soft and warm, friendly = casual and light, firm = clear and direct but always respectful; never threatening, never mention fees, legal action or shame).`,
    `Write it in ${r.language}. It will be sent as a ${r.channel === 'email' ? 'short email body' : 'chat message'}: plain text, at most ${limit} characters, no markdown, no subject line, no placeholders like [name].`,
    'Include the exact amount and the date from FACTS. Greet the person by the name given. Do not invent any other facts, account numbers, links or payment details. Sign off with the name of the user only if one is given.',
    'Do not mention AI. Reply with ONLY the message text.',
  ].join('\n');
}

export function userPrompt(r: MessageRequest): string {
  const facts = {
    person: r.personName,
    direction: r.direction,
    amount_rupees: r.amount,
    due_date: r.dueDate,
    days_overdue: r.overdueDays,
    from: r.userName ?? null,
  };
  return [
    `FACTS: ${JSON.stringify(facts)}`,
    r.context ? `ADDITIONAL CONTEXT FROM THE USER (use it naturally): ${r.context}` : '',
    r.previous ? `The user did not like this draft, write a clearly different one:\n${r.previous}` : '',
  ].filter(Boolean).join('\n\n');
}

/** Amount as people write it: 1500 -> "1500" or "1,500" (with or without a rupee sign). */
function amountForms(amount: number): string[] {
  const whole = Math.round(amount);
  return [String(whole), whole.toLocaleString('en-IN'), whole.toLocaleString('en-US')];
}

/**
 * The model's reply, cleaned and checked, or null when it cannot be trusted: it must mention the person,
 * contain the real amount, hold no leftover placeholders or links, and fit the channel.
 */
export function checkDraft(text: string, r: MessageRequest): string | null {
  const t = text.replace(/^["'`\s]+|["'`\s]+$/g, '').replace(/\*\*|__/g, '').trim();
  if (t.length < 15) return null;
  if (t.length > (r.channel === 'sms' ? 400 : r.channel === 'email' ? 1200 : 700)) return null;
  // Hindi digits and a transliterated name are fine: compare amounts with ASCII digits, and only insist on the name
  // when the language uses the same letters as the stored name.
  const ascii = t.replace(/[०-९]/g, (d) => String(d.charCodeAt(0) - 0x0966));
  const first = r.personName.split(/\s+/)[0].toLowerCase();
  if (r.language !== 'Hindi' && !t.toLowerCase().includes(first)) return null;
  if (!amountForms(r.amount).some((a) => ascii.includes(a))) return null;
  if (/\[[^\]]{1,30}\]|\{[^}]{1,30}\}|<[^>]{1,30}>/.test(t)) return null; // a leftover [Name] / {amount} placeholder
  if (/https?:\/\/|www\./i.test(t)) return null; // we never put links in a money message
  return t;
}
