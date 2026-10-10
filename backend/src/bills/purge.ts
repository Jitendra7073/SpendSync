import { and, eq, inArray, lte, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { billPurges } from '../db/schema/index';
import { config } from '../config/env';
import { logger } from '../utils/logger';
import { destroyAsset, destroyPrefix } from './cloudinary';
import { purgeBackoffMs } from './rules';

export type Tx = Parameters<Parameters<typeof db.transaction>[0]>[0];

/** Records Cloudinary deletions inside the caller's DB transaction, so they survive the row deletes. */
export async function enqueuePurges(tx: Tx, targets: string[], kind: 'asset' | 'prefix' = 'asset'): Promise<string[]> {
  if (targets.length === 0) return [];
  const rows = await tx.insert(billPurges).values(targets.map((target) => ({ target, kind }))).returning({ id: billPurges.id });
  return rows.map((r) => r.id);
}

/** Tries the given purges (or every due one). Success deletes the row; failure backs off. Never throws. */
export async function runPurges(opts: { ids?: string[]; limit?: number } = {}) {
  const c = config.bills.cloudinary;
  if (!c) return { done: 0, failed: 0 };
  const due = await db
    .select()
    .from(billPurges)
    .where(opts.ids ? inArray(billPurges.id, opts.ids.length ? opts.ids : ['00000000-0000-0000-0000-000000000000']) : lte(billPurges.nextAt, new Date()))
    .limit(opts.limit ?? 100);

  let done = 0;
  let failed = 0;
  for (const p of due) {
    try {
      if (p.kind === 'prefix') {
        const r = await destroyPrefix(c, p.target);
        if (r.partial) throw new Error('more files left under prefix');
      } else {
        await destroyAsset(c, p.target);
      }
      await db.delete(billPurges).where(eq(billPurges.id, p.id));
      done++;
    } catch (e) {
      failed++;
      const attempts = p.attempts + 1;
      await db
        .update(billPurges)
        .set({ attempts, nextAt: new Date(Date.now() + purgeBackoffMs(attempts)), lastError: e instanceof Error ? e.message.slice(0, 500) : String(e) })
        .where(and(eq(billPurges.id, p.id), eq(billPurges.attempts, sql`${p.attempts}`)));
      if (attempts >= 10) logger.error('Bill purge keeps failing', { target: p.target, attempts });
    }
  }
  return { done, failed };
}
