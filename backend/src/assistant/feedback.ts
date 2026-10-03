import { desc, eq } from 'drizzle-orm';
import { z } from 'zod';
import { db } from '../db/index';
import { assistantFeedback } from '../db/schema/index';

/** Reason codes the app offers on a thumbs-down. The phone shows them in the user's language. */
export const FEEDBACK_REASONS = ['wrong', 'not_understood', 'no_action', 'too_slow', 'too_long', 'too_short', 'wrong_language', 'confusing', 'other'] as const;
export type FeedbackReason = (typeof FEEDBACK_REASONS)[number];

export const feedbackSchema = z.object({
  conversationId: z.string().min(8).max(64),
  messageRef: z.string().max(64).default(''),
  rating: z.enum(['up', 'down']),
  reasons: z.array(z.enum(FEEDBACK_REASONS)).max(9).default([]),
  comment: z.string().trim().max(500).default(''),
  question: z.string().max(600).default(''),
  answer: z.string().max(1500).default(''),
  model: z.string().max(100).default(''),
});
export type FeedbackInput = z.infer<typeof feedbackSchema>;

export async function saveFeedback(userId: string, f: FeedbackInput): Promise<void> {
  await db.insert(assistantFeedback).values({
    userId,
    conversationId: f.conversationId,
    messageRef: f.messageRef,
    rating: f.rating,
    reasons: f.reasons.join(','),
    comment: f.comment,
    question: f.question,
    answer: f.answer,
    model: f.model,
  });
}

const HINT: Record<FeedbackReason, string> = {
  wrong: 'Double-check every number with a tool before answering; say when unsure.',
  not_understood: 'If the request is unclear, restate what you understood in one short line before acting.',
  no_action: 'When the user wants something added or changed, prepare it with propose_entry or offer the screen instead of only explaining.',
  too_slow: 'Keep answers short and avoid unnecessary tool calls.',
  too_long: 'Keep answers much shorter.',
  too_short: 'Give a little more detail.',
  wrong_language: 'Reply in exactly the language the user writes in.',
  confusing: 'Use simpler words and shorter sentences.',
  other: '',
};

/**
 * Turns a user's recent thumbs-down reasons into standing hints for their next answers.
 * A reason must appear at least twice in the last 20 ratings, or be on the latest rating, so one
 * grumpy tap does not change the assistant's behaviour for good.
 */
export function hintsFromFeedback(rows: Array<{ rating: string; reasons: string }>): string[] {
  const recent = rows.slice(0, 20);
  const counts = new Map<FeedbackReason, number>();
  for (const r of recent) {
    if (r.rating !== 'down') continue;
    for (const code of r.reasons.split(',')) {
      if (code in HINT) counts.set(code as FeedbackReason, (counts.get(code as FeedbackReason) ?? 0) + 1);
    }
  }
  const latest = recent[0]?.rating === 'down' ? recent[0].reasons.split(',') : [];
  const hints: string[] = [];
  for (const [code, n] of counts) {
    if (!HINT[code]) continue;
    if (n >= 2 || latest.includes(code)) hints.push(HINT[code]);
  }
  return hints.slice(0, 4);
}

export async function loadFeedbackHints(userId: string): Promise<string[]> {
  try {
    const rows = await db
      .select({ rating: assistantFeedback.rating, reasons: assistantFeedback.reasons })
      .from(assistantFeedback)
      .where(eq(assistantFeedback.userId, userId))
      .orderBy(desc(assistantFeedback.createdAt))
      .limit(20);
    return hintsFromFeedback(rows);
  } catch {
    return []; // learning is a bonus; never block an answer on it
  }
}
