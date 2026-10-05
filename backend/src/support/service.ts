import { and, desc, eq } from 'drizzle-orm';
import { db } from '../db/index';
import { supportTickets, user } from '../db/schema/index';
import { sendEmail } from '../lib/email';
import { NotFoundError } from '../utils/errors';
import { logger } from '../utils/logger';
import { supportRecipients } from './recipients';
import { composeReceiptEmail } from './templates';
import { composeTicketEmail, newTicketRef, type CreateTicketInput, type TicketCategory } from './ticket';


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
  // The email shows who wrote it: their name as the sender, their address as Reply-To.
  // Two emails: the team gets the full report; the person who sent it gets a "we got it" confirmation
  // (Reply-To = the support inbox, so their reply reaches the team).
  const receipt = u?.email
    ? composeReceiptEmail({ ref, category: input.category as TicketCategory, message: input.message, context: input.context, user: { name: u.name, email: u.email }, createdAt: row.createdAt })
    : null;
  const [result, receiptResult] = await Promise.all([
    sendEmail({ to, ...mail, replyTo: u?.email ?? undefined, fromName: u?.name ? `${u.name} (SpendSync user)` : undefined }),
    receipt ? sendEmail({ to: u!.email, ...receipt, replyTo: to[0], fromName: 'SpendSync Support' }) : Promise.resolve(null),
  ]);
  if (receiptResult && !receiptResult.ok) logger.warn('support receipt email failed', { ref, error: receiptResult.error });
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

/** Closes one of the user's own reports. Only their own, and only moving to `closed`; the report itself stays on record. */
export async function closeTicket(userId: string, ref: string) {
  const [row] = await db
    .update(supportTickets)
    .set({ status: 'closed' })
    .where(and(eq(supportTickets.userId, userId), eq(supportTickets.ref, ref)))
    .returning({ ref: supportTickets.ref, status: supportTickets.status });
  if (!row) throw new NotFoundError('Report not found');
  return row;
}
