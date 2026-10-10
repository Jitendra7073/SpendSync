import { and, eq, inArray, lt } from 'drizzle-orm';
import { db } from '../db/index';
import { billUploadCounts, bills } from '../db/schema/index';
import { config } from '../config/env';
import { MAX_BILL_BYTES, listAssets } from './cloudinary';
import { enqueuePurges, runPurges } from './purge';
import { BILLS_ROOT, utcDay } from './rules';

const DAY = 86_400_000;
const MAX_PAGES_PER_RUN = 10; // 10 × 500 assets reconciled per run

export async function runBillsCron(now = new Date()) {
  const c = config.bills.cloudinary;
  if (!c) return { expired: 0, purged: 0, reconciled: 0, orphans: 0, oversized: 0 };

  // 1. Reservations nobody confirmed within a day.
  const expired = await db.transaction(async (tx) => {
    // trash-aware: stale reservations go whether or not they are in the Trash
    const stale = await tx.select({ id: bills.id, publicId: bills.publicId }).from(bills).where(and(eq(bills.status, 'pending'), lt(bills.createdAt, new Date(now.getTime() - DAY)))).limit(500);
    await enqueuePurges(tx, stale.map((s) => s.publicId));
    if (stale.length) await tx.delete(bills).where(inArray(bills.id, stale.map((s) => s.id)));
    return stale.length;
  });

  // 2. Reconcile real sizes from Cloudinary; remove orphans and oversized files.
  let reconciled = 0;
  let orphans = 0;
  let oversized = 0;
  let cursor: string | undefined;
  for (let i = 0; i < MAX_PAGES_PER_RUN; i++) {
    const page = await listAssets(c, BILLS_ROOT, cursor);
    const ids = page.items.map((a) => a.publicId);
    // trash-aware: Trash files are real files too
    const rows = ids.length ? await db.select({ id: bills.id, publicId: bills.publicId }).from(bills).where(inArray(bills.publicId, ids)) : [];
    const known = new Map(rows.map((r) => [r.publicId, r.id]));
    const toPurge: string[] = [];
    for (const a of page.items) {
      const id = known.get(a.publicId);
      if (!id) {
        toPurge.push(a.publicId);
        orphans++;
      } else if (a.bytes > MAX_BILL_BYTES) {
        toPurge.push(a.publicId);
        oversized++;
        await db.delete(bills).where(eq(bills.id, id));
      } else {
        await db.update(bills).set({ bytes: a.bytes }).where(eq(bills.id, id));
        reconciled++;
      }
    }
    if (toPurge.length) await db.transaction((tx) => enqueuePurges(tx, toPurge));
    if (!page.next) break;
    cursor = page.next;
  }

  // 3. Drain the outbox; 4. drop old daily counters.
  const { done } = await runPurges({ limit: 500 });
  await db.delete(billUploadCounts).where(lt(billUploadCounts.day, utcDay(new Date(now.getTime() - 7 * DAY))));
  return { expired, purged: done, reconciled, orphans, oversized };
}
