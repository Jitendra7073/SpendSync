import { desc, eq } from 'drizzle-orm';
import { db } from '../db/index';
import { assistantAudit } from '../db/schema/index';
import { logger } from '../utils/logger';
import type { ToolTier } from './permissions';

export interface ToolCallRecord {
  userId: string;
  conversationId: string;
  toolName: string;
  tier: ToolTier;
  ok: boolean;
  durationMs: number;
  error?: string;
}

/** Best-effort: a logging failure must never break (or slow) the user's answer. */
export function logToolCall(rec: ToolCallRecord): void {
  db.insert(assistantAudit)
    .values({
      userId: rec.userId,
      conversationId: rec.conversationId,
      toolName: rec.toolName,
      tier: rec.tier,
      ok: rec.ok,
      durationMs: rec.durationMs,
      error: rec.error?.slice(0, 300),
    })
    .catch((err: unknown) => logger.warn('assistant audit insert failed', { message: String(err) }));
}

export async function recentToolCalls(userId: string, limit = 50) {
  return db
    .select()
    .from(assistantAudit)
    .where(eq(assistantAudit.userId, userId))
    .orderBy(desc(assistantAudit.createdAt))
    .limit(Math.min(limit, 200));
}
