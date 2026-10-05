import { z } from 'zod';

export const SUPPORTED_ASSISTANT_LANGUAGES = ['English', 'Hindi', 'Spanish', 'French', 'German'] as const;

/** Body of POST /api/assistant/chat. The server is stateless: the phone sends the recent turns. */
export const chatRequestSchema = z
  .object({
    messages: z
      .array(
        z.object({
          role: z.enum(['user', 'assistant']),
          content: z.string().min(1).max(4000),
        }),
      )
      .min(1)
      .max(20),
    language: z.enum(SUPPORTED_ASSISTANT_LANGUAGES).default('English'),
    screen: z.enum(['home', 'analytics', 'budget', 'planify', 'profile', 'holds', 'add_transaction']).optional(),
    /** IANA timezone, e.g. Asia/Kolkata, so "today" and "this month" match the user's day. */
    timezone: z.string().max(64).optional(),
    conversationId: z.string().min(8).max(64),
    /** The user's assistant settings (Settings → Assistant). Sent every time so edits apply instantly, even before they sync. */
    prefs: z
      .object({
        model: z.string().max(100).default('auto'),
        disabledTools: z.array(z.string().max(60)).max(30).default([]),
        style: z.enum(['short', 'balanced', 'detailed']).default('balanced'),
        tone: z.enum(['simple', 'friendly', 'professional']).default('friendly'),
        instructions: z.string().max(300).default(''),
      })
      .default({}),
  })
  .refine((b) => b.messages.at(-1)?.role === 'user', {
    message: 'The last message must be from the user',
  });

export type ChatRequest = z.infer<typeof chatRequestSchema>;
export type AssistantPrefs = ChatRequest['prefs'];

/** Today's date (YYYY-MM-DD) in the user's timezone; falls back to UTC for an unknown zone. */
export function todayIn(timezone: string | undefined, now = new Date()): string {
  try {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: timezone || 'UTC',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(now);
  } catch {
    return now.toISOString().slice(0, 10);
  }
}
