import { desc, eq } from 'drizzle-orm';
import { db } from '../db/index';
import { supportTickets, user } from '../db/schema/index';
import { sendEmail } from '../lib/email';
import { logger } from '../utils/logger';
import { composeTicketEmail, newTicketRef, type CreateTicketInput, type TicketCategory } from './ticket';

/** Where reports go. Comma-separated; set SUPPORT_EMAIL_TO in the server environment. */
function supportRecipients(): string[] {
  return (process.env.SUPPORT_EMAIL_TO ?? '').split(',').map((s) => s.trim()).filter(Boolean);
}

/**
 * Saves the report first (so it is never lost), then emails the team a copy and records how that went.
 * A mail problem leaves the ticket "open" with email_status failed/not_configured; the user still gets
 * their reference and the team can read it in the database.
 */
export async function createTicket(userId: string, input: CreateTicketInput) {
  const [u] = await db.select({ id: user.id, name: user.name, email: user.email }).from(user).where(eq(user.id, userId)).limit(1);
  const ref = newTicketRef();
  const [row] = await db
    .insert(supportTickets)
    .values({ ref, userId, category: input.category, message: input.message, context: JSON.stringify(input.context) })
    .returning();

  const to = supportRecipients();
  const mail = composeTicketEmail({
    ref,
    category: input.category as TicketCategory,
    message: input.message,
    context: input.context,
    user: { id: userId, name: u?.name, email: u?.email },
    createdAt: row.createdAt,
  });
  const result = await sendEmail({ to, ...mail, replyTo: u?.email ?? undefined });
  const emailStatus = result.ok ? 'sent' : result.reason === 'not_configured' ? 'not_configured' : 'failed';
  if (!result.ok && result.reason === 'failed') logger.warn('support email failed', { ref, error: result.error });
  await db
    .update(supportTickets)
    .set({ emailStatus, emailError: result.ok ? null : (result.error ?? null) })
    .where(eq(supportTickets.id, row.id));

  return { ref, status: row.status, emailStatus, createdAt: row.createdAt };
}

/** The user's own reports, newest first: the record shown inside the app. */
export async function listTickets(userId: string, limit = 30) {
  return db
    .select({
      ref: supportTickets.ref,
      category: supportTickets.category,
      message: supportTickets.message,
      status: supportTickets.status,
      emailStatus: supportTickets.emailStatus,
      createdAt: supportTickets.createdAt,
    })
    .from(supportTickets)
    .where(eq(supportTickets.userId, userId))
    .orderBy(desc(supportTickets.createdAt))
    .limit(Math.min(limit, 100));
}
