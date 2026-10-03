/** Read-only: lists the latest support tickets and whether their email went out. Run: npx vite-node scripts/probe-tickets.ts */
import { config } from 'dotenv';

config({ path: '.env' });

async function main() {
  const { db } = await import('../src/db/index');
  const { supportTickets } = await import('../src/db/schema/index');
  const { desc } = await import('drizzle-orm');
  try {
    const rows = await db
      .select({ ref: supportTickets.ref, category: supportTickets.category, emailStatus: supportTickets.emailStatus, emailError: supportTickets.emailError, createdAt: supportTickets.createdAt })
      .from(supportTickets)
      .orderBy(desc(supportTickets.createdAt))
      .limit(10);
    console.log(rows.length ? rows.map((r) => `${r.ref} | ${r.category} | email: ${r.emailStatus}${r.emailError ? ` (${r.emailError})` : ''} | ${r.createdAt.toISOString()}`).join('\n') : 'No tickets saved yet.');
  } catch (e) {
    console.log('Query failed:', (e instanceof Error ? e.message : String(e)).slice(0, 200));
  }
  process.exit(0);
}

void main();
