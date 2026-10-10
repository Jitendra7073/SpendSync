import { isNull } from 'drizzle-orm';
import { bills, holds, transactions } from '../db/schema/index';

/** Rows not in the Trash. Add to EVERY read of these tables (live.test.ts enforces it). */
export const liveTx = isNull(transactions.deletedAt);
export const liveHold = isNull(holds.deletedAt);
export const liveBill = isNull(bills.deletedAt);
