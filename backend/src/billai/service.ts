import { and, asc, eq, lt, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { billReadCounts, billReads, bills, type BillReadStatus } from '../db/schema/index';
import { config } from '../config/env';
import { liveBill } from '../lib/live';
import { ProviderRouter, AllProvidersFailed } from '../assistant/llm/router';
import { buildVisionProviders } from '../assistant/llm/chain';
import { validating } from '../assistant/llm/validating';
import { askWith } from '../assistant/llm/vision';
import type { LlmImage } from '../assistant/llm/types';
import { requireCloudinary } from '../bills/service';
import { checkDaily, utcDay } from '../bills/rules';
import { transactionService } from '../services/transaction.service';
import { logger } from '../utils/logger';
import { parseBillRead, type BillRead } from './parse';
import { buildBillPrompt, BILL_USER_TEXT } from './prompt';
import { pagePlan, pagesKey, readImageUrl } from './pages';
import type { BillAiPrefs } from './schema';

export type ReadState = { status: 'none' | BillReadStatus; data: BillRead | null; model: string | null; stale: boolean };
const MAX_ATTEMPTS = 5;
const DEFAULT_PREFS: BillAiPrefs = { language: 'English', style: 'balanced', tone: 'friendly' };

let router: ProviderRouter | null = null;
/** Own router: vision cool-downs are separate from chat. Invalid JSON counts as a failed model. */
function visionRouter() {
  return (router ??= new ProviderRouter(
    buildVisionProviders().map((p) => validating(p, (t) => void parseBillRead(t))),
    { firstTokenMs: 40_000, idleMs: 40_000 },
  ));
}

async function countRead(userId: string) {
  const [{ count }] = await db
    .insert(billReadCounts)
    .values({ userId, day: utcDay(new Date()), count: 1 })
    .onConflictDoUpdate({ target: [billReadCounts.userId, billReadCounts.day], set: { count: sql`${billReadCounts.count} + 1` } })
    .returning({ count: billReadCounts.count });
  checkDaily(count, config.billAi.maxPerDay);
}

async function livePages(userId: string, txId: string) {
  return db
    .select({ id: bills.id, publicId: bills.publicId, version: bills.version, format: bills.format, pages: bills.pages })
    .from(bills)
    .where(and(eq(bills.transactionId, txId), eq(bills.userId, userId), eq(bills.status, 'ready'), liveBill))
    .orderBy(asc(bills.position));
}

async function fetchImages(pages: Awaited<ReturnType<typeof livePages>>): Promise<LlmImage[]> {
  const c = requireCloudinary();
  const out: LlmImage[] = [];
  for (const p of pagePlan(pages)) {
    const r = await fetch(readImageUrl(c, p.publicId, p.version, p.page), { signal: AbortSignal.timeout(10_000) });
    if (!r.ok) throw new Error(`page fetch ${r.status}`);
    const buf = Buffer.from(await r.arrayBuffer());
    if (buf.length > 2 * 1024 * 1024) throw new Error('page image too large');
    out.push({ mime: 'image/jpeg', base64: buf.toString('base64') });
  }
  return out;
}

async function ask(images: LlmImage[], prefs: BillAiPrefs) {
  const { text, model } = await askWith(visionRouter(), buildBillPrompt(prefs), BILL_USER_TEXT, images);
  return { data: parseBillRead(text), model };
}

export const billReadService = {
  async get(userId: string, txId: string): Promise<ReadState> {
    await transactionService.getById(userId, txId);
    const pages = await livePages(userId, txId);
    const [row] = await db.select().from(billReads).where(and(eq(billReads.transactionId, txId), eq(billReads.userId, userId)));
    if (!row || pages.length === 0) return { status: 'none', data: null, model: null, stale: false };
    return { status: row.status, data: (row.data as BillRead | null) ?? null, model: row.model, stale: row.pagesKey !== pagesKey(pages) };
  },

  async run(userId: string, txId: string, opts: { consent: boolean; force?: boolean; prefs?: BillAiPrefs }): Promise<ReadState> {
    await transactionService.getById(userId, txId);
    const pages = await livePages(userId, txId);
    if (pages.length === 0) return { status: 'none', data: null, model: null, stale: false };
    const key = pagesKey(pages);
    const [row] = await db.select().from(billReads).where(and(eq(billReads.transactionId, txId), eq(billReads.userId, userId)));

    if (!opts.consent) {
      await db.insert(billReads).values({ transactionId: txId, userId, status: 'no_consent', pagesKey: key })
        .onConflictDoUpdate({ target: billReads.transactionId, set: { status: 'no_consent', pagesKey: key, updatedAt: new Date() } });
      return this.get(userId, txId);
    }
    if (row && row.status === 'done' && row.pagesKey === key && !opts.force) return this.get(userId, txId);

    await countRead(userId); // throws 429 DAILY_LIMIT
    await db.insert(billReads).values({ transactionId: txId, userId, status: 'queued', pagesKey: key })
      .onConflictDoUpdate({ target: billReads.transactionId, set: { status: 'queued', pagesKey: key, updatedAt: new Date() } });

    try {
      const { data, model } = await ask(await fetchImages(pages), opts.prefs ?? DEFAULT_PREFS);
      // Compare-and-set: pages changed during the read → this result is for old pages, drop it.
      await db.update(billReads).set({ status: 'done', data, model, error: null, attempts: 0, updatedAt: new Date() })
        .where(and(eq(billReads.transactionId, txId), eq(billReads.pagesKey, key)));
    } catch (e) {
      const attempts = (row?.pagesKey === key ? row.attempts : 0) + 1;
      await db.update(billReads)
        .set({ status: attempts >= MAX_ATTEMPTS ? 'failed' : 'queued', attempts, error: e instanceof AllProvidersFailed ? 'models unavailable' : 'read failed', updatedAt: new Date() })
        .where(and(eq(billReads.transactionId, txId), eq(billReads.pagesKey, key)));
      logger.warn('Bill read failed', { txId, attempts, message: e instanceof Error ? e.message.slice(0, 200) : String(e) });
    }
    return this.get(userId, txId);
  },

  /** Pre-fill: read images the phone sent, store nothing. */
  async readImages(userId: string, images: LlmImage[], prefs: BillAiPrefs) {
    await countRead(userId);
    return ask(images, prefs);
  },

  /** Cron: queued rows (only ever created by a consenting request), oldest first. */
  async retryQueued(limit = 50): Promise<number> {
    const rows = await db.select({ txId: billReads.transactionId, userId: billReads.userId })
      .from(billReads).where(and(eq(billReads.status, 'queued'), lt(billReads.attempts, MAX_ATTEMPTS)))
      .orderBy(asc(billReads.updatedAt)).limit(limit);
    for (const r of rows) {
      await this.run(r.userId, r.txId, { consent: true }).catch((e) => logger.warn('Bill read retry failed', { txId: r.txId, message: String(e).slice(0, 200) }));
    }
    return rows.length;
  },
};
