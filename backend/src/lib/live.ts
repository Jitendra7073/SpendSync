import { isNull } from 'drizzle-orm';
import { holds, transactions } from '../db/schema/index';

/** Rows not in the Trash. Add to EVERY read of these tables (live.test.ts enforces it). */
export const liveTx = isNull(transactions.deletedAt);
export const liveHold = isNull(holds.deletedAt);
