# Trash / Soft Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deleting a transaction or hold moves it to a Trash (restorable, hidden from every list/total) instead of erasing it; Settings → Trash restores or deletes forever.

**Architecture:** Nullable `deleted_at` on `transactions` and `holds`. One shared Drizzle filter (`liveTx` / `liveHold`) is added to every read; a source-scanning guard test fails if a query forgets it. Trash listing/hard delete live in `backend/src/trash/`; Android gets an Undo bar on the shared toast and a new Settings page.

**Tech Stack:** Next.js App Router, Drizzle ORM 0.33 on postgres-js, zod, vitest · Kotlin, Jetpack Compose, Retrofit, WorkManager.

**Spec:** `docs/superpowers/specs/2026-10-10-trash-soft-delete-design.md`

## Global Constraints

- Schema changes go in BOTH `backend/src/db/schema/*.schema.ts` and `backend/src/db/schema.ts`.
- **Never run `npm run db:push` or any DDL** — the user applies schema changes.
- Every route export is `withApi(handler, { auth: 'required' })` and the file ends with `export { corsPreflight as OPTIONS };`.
- Every statement filters on the signed-in `userId`; ids are validated with `z.string().uuid()`.
- Android: no string literals in UI — `tr(R.string.x)`; every new key in `values`, `values-hi`, `values-es`, `values-fr`, `values-de` (`I18nResourcesTest`).
- Android: amounts only through `MaskableAmountText`; buttons `AppButton`; dialogs `AppConfirmDialog`; colours from theme / `incomeColor()` / `expenseColor()`.
- Items stay in Trash until the user deletes them — no auto-purge.

## Deliberate deviations from the spec (decided while planning)

- `DELETE /api/transactions/:id` returns **200 `{ holdIds }`** instead of 204, so the phone can cancel those holds' reminders.
- `POST /api/transactions/:id/restore` returns `{ transaction, holds }` (holds restored with it) so the phone can reschedule reminders.
- The Trash list nests **all** deleted holds of a deleted transaction (they go with it on hard delete); restore still brings back only the ones deleted together with it.
- The Settings page host is a scrolling `Column`, so paging is a **Load more** button, not scroll-triggered; no pull-to-refresh (the page reloads on open). Empty-Trash confirm does not show a count (unknown across pages).
- No Android unit test for Undo ordering: the home list is sorted at render time (`sortedByDescending { it.createdAt }`), so re-adding the row needs no logic.
- Added (security): `holdService.create` now checks the transaction belongs to the user and is live — today any `transactionId` is accepted.

## Review Focus

1. **Double tap on Delete / Undo** — second DELETE or restore must be a harmless no-op (idempotent), never a 404 toast. Pinned by Task 2 tests (`assertInTrash`) and the idempotent branches in Task 3.
2. **Deleting a transaction, then restoring it when one of its holds was deleted earlier** — the earlier hold must stay in Trash. Pinned by the SQL in Task 3 (`restore` matches on the parent's `deleted_at`); verify manually in Task 8 checklist.
3. **Hard delete of a live item via a stolen token** — must 409, not erase. Pinned by `assertInTrash` tests (Task 2).
4. **Bad / forged `cursor` query param** — must 400, never 500 or an SQL error. Pinned by `decodeCursor` tests (Task 2).
5. **Undo after the hold screen closes** (last hold of a person deleted) — the item must still be restorable from Settings → Trash. Verify manually in Task 8 checklist.

---

### Task 1: Schema columns, the shared live filter, and the guard test

**Files:**
- Modify: `backend/src/db/schema/transactions.schema.ts`
- Modify: `backend/src/db/schema/holds.schema.ts`
- Modify: `backend/src/db/schema.ts` (tables `transactions` ~line 66, `holds` ~line 109)
- Create: `backend/src/lib/live.ts`
- Create: `backend/src/lib/live.test.ts`
- Modify (add the filter): `backend/src/services/transaction.service.ts`, `backend/src/services/dashboard.service.ts`, `backend/src/services/hold.service.ts`, `backend/src/planify/service.ts`, `backend/src/planify/auto-classify.service.ts`, `backend/src/export/service.ts`, `backend/src/assistant/tools.ts`

**Interfaces:**
- Produces: `transactions.deletedAt`, `holds.deletedAt` (Drizzle `timestamp` columns, `Date | null`); `liveTx`, `liveHold` (Drizzle `SQL` conditions) exported from `backend/src/lib/live.ts`.

- [ ] **Step 1: Write the failing guard test**

`backend/src/lib/live.test.ts`:

```ts
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = join(__dirname, '..');

function tsFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return tsFiles(p);
    return p.endsWith('.ts') && !p.endsWith('.test.ts') ? [p] : [];
  });
}

/**
 * Every read of transactions/holds must skip the Trash. For each `.from(transactions|holds)` the
 * enclosing function (from the nearest `async ` before it to the end of the statement) must mention
 * the live filter, `deletedAt`, or a `trash-aware` comment (Trash code that reads deleted rows on purpose).
 * ponytail: text heuristic, not a parser — one function with two queries passes if either has it.
 */
describe('Trash guard', () => {
  it('every query on transactions/holds filters deleted rows', () => {
    const misses: string[] = [];
    for (const file of tsFiles(SRC)) {
      const src = readFileSync(file, 'utf8');
      for (const m of src.matchAll(/\.from\((transactions|holds)\)/g)) {
        const at = m.index ?? 0;
        const start = Math.max(0, src.lastIndexOf('async ', at));
        const end = src.indexOf(';', at);
        const window = src.slice(start, end === -1 ? undefined : end);
        const filter = m[1] === 'transactions' ? 'liveTx' : 'liveHold';
        if (!window.includes(filter) && !window.includes('deletedAt') && !window.includes('trash-aware')) {
          const line = src.slice(0, at).split('\n').length;
          misses.push(`${relative(SRC, file)}:${line} .from(${m[1]})`);
        }
      }
    }
    expect(misses).toEqual([]);
  });
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd backend && npx vitest run src/lib/live.test.ts`
Expected: FAIL — the `misses` array lists ~18 sites (assistant/tools.ts, export/service.ts, planify/…, services/…).

- [ ] **Step 3: Add the columns (both schema files)**

`backend/src/db/schema/transactions.schema.ts` — change the import and table:

```ts
import { pgTable, uuid, text, decimal, timestamp, index } from 'drizzle-orm/pg-core';
```

```ts
export const transactions = pgTable(
  'transactions',
  {
    // ...all existing columns unchanged...
    createdAt: timestamp('created_at').notNull().defaultNow(),
    updatedAt: timestamp('updated_at').notNull().defaultNow(),
    // Set when moved to Trash; null = live. Every read filters on it (see src/lib/live.ts).
    deletedAt: timestamp('deleted_at'),
  },
  (t) => ({ userDeleted: index('transactions_user_deleted_idx').on(t.userId, t.deletedAt) }),
);
```

`backend/src/db/schema/holds.schema.ts` — same pattern:

```ts
import { pgTable, uuid, text, decimal, timestamp, index } from 'drizzle-orm/pg-core';
```

```ts
export const holds = pgTable(
  'holds',
  {
    // ...all existing columns unchanged...
    updatedAt: timestamp('updated_at').notNull().defaultNow(),
    // Set when moved to Trash (with its transaction, or on its own); null = live.
    deletedAt: timestamp('deleted_at'),
  },
  (t) => ({ userDeleted: index('holds_user_deleted_idx').on(t.userId, t.deletedAt) }),
);
```

`backend/src/db/schema.ts` — add `index` to the `drizzle-orm/pg-core` import, and make the two tables identical to the above (same column `deletedAt: timestamp('deleted_at')`, same third argument with the same index names).

- [ ] **Step 4: Create the shared filter**

`backend/src/lib/live.ts`:

```ts
import { isNull } from 'drizzle-orm';
import { holds, transactions } from '../db/schema/index';

/** Rows not in the Trash. Add to EVERY read of these tables (live.test.ts enforces it). */
export const liveTx = isNull(transactions.deletedAt);
export const liveHold = isNull(holds.deletedAt);
```

- [ ] **Step 5: Add the filter to every read**

Add `import { liveTx } from '../lib/live';` (and/or `liveHold`) to each file, then:

`services/transaction.service.ts`
- `getAll`: `const conditions = [eq(transactions.userId, userId), liveTx];`
- `getById`: `.where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId), liveTx));`
- `update`: the `.update(...).where(...)` becomes `.where(and(eq(transactions.id, transactionId), eq(transactions.userId, userId), liveTx))`.
- (`delete` is rewritten in Task 3 — leave it for now.)

`services/dashboard.service.ts` — add `liveTx` inside each of the three `and(...)`:
- category spending: `and(eq(transactions.userId, userId), liveTx, gte(...), lt(...))`
- `getMonthlyTrend`: `and(eq(transactions.userId, userId), liveTx, gte(...))`
- `getTopMerchants`: `and(eq(transactions.userId, userId), liveTx, eq(transactions.type, 'debit'))`

`services/hold.service.ts`
- `getAll`: `const conditions = [eq(holds.userId, userId), liveHold];`
- `getById`: `.where(and(eq(holds.id, holdId), eq(holds.userId, userId), liveHold));`
- `update`: `.where(and(eq(holds.id, holdId), eq(holds.userId, userId), liveHold))`

`planify/service.ts` — add `liveTx` to the four `and(eq(transactions.userId, userId), ...)` calls (`txsBetween`, both queries in `loadHistory`, `loadCredits`), e.g.
`.where(and(eq(transactions.userId, userId), liveTx, gte(transactions.createdAt, start), lt(transactions.createdAt, end)))`.
Import path: `import { liveTx } from '../lib/live';`

`planify/auto-classify.service.ts` — `and(eq(transactions.userId, userId), liveTx, eq(transactions.type, 'debit'), gt(...))`.

`export/service.ts`
- transactions: `.where(and(eq(transactions.userId, userId), liveTx, gte(...), lt(...)))`
- holds: `.where(and(eq(holds.userId, userId), liveHold))` (add `and` is already imported).

`assistant/tools.ts`
- balance tool (~line 98): `.where(and(eq(transactions.userId, ctx.userId), liveTx))`
- search tool: where its `conditions` array is first built (just above line 205, starts with `eq(transactions.userId, ctx.userId)`), add `liveTx` as the second element.

- [ ] **Step 6: Run the guard and the full suite**

Run: `cd backend && npx vitest run src/lib/live.test.ts` → Expected: PASS
Run: `cd backend && npx tsc --noEmit -p .` → Expected: no errors
Run: `cd backend && npm test` → Expected: all pass

- [ ] **Step 7: Commit**

```bash
git add backend/src/db backend/src/lib/live.ts backend/src/lib/live.test.ts backend/src/services backend/src/planify backend/src/export/service.ts backend/src/assistant/tools.ts
git commit -m "feat(trash): deleted_at columns and live-row filter on every read"
```

---

### Task 2: Pure Trash rules (cursor, merge, guards)

**Files:**
- Create: `backend/src/trash/rules.ts`
- Create: `backend/src/trash/rules.test.ts`

**Interfaces:**
- Produces:
  - `interface TrashCursor { at: string; id: string }` (`at` = ISO timestamp)
  - `encodeCursor(c: TrashCursor): string`
  - `decodeCursor(s: string | undefined): TrashCursor | null` — throws `BadRequestError` (400, code `BAD_CURSOR`) when malformed
  - `mergePage<T extends { id: string; deletedAt: Date }>(a: T[], b: T[], limit: number): { items: T[]; nextCursor: string | null }`
  - `assertInTrash(row: { deletedAt: Date | null } | undefined): void` — `NotFoundError` if missing, `ConflictError(…, 'NOT_IN_TRASH')` if live
  - `assertParentLive(parentDeletedAt: Date | null): void` — `ConflictError(…, 'PARENT_DELETED')` if not null

- [ ] **Step 1: Write the failing tests**

`backend/src/trash/rules.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { assertInTrash, assertParentLive, decodeCursor, encodeCursor, mergePage } from './rules';

const id = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const row = (n: number, iso: string) => ({ id: id(n), deletedAt: new Date(iso) });

describe('cursor', () => {
  it('round-trips', () => {
    const c = { at: '2026-10-10T10:00:00.000Z', id: id(1) };
    expect(decodeCursor(encodeCursor(c))).toEqual(c);
  });
  it('is null when absent', () => {
    expect(decodeCursor(undefined)).toBeNull();
    expect(decodeCursor('')).toBeNull();
  });
  it('rejects forged or broken cursors with 400', () => {
    for (const bad of ['not-base64!!', Buffer.from('{"at":"x","id":"y"}').toString('base64url'), Buffer.from('[]').toString('base64url'), Buffer.from(`{"at":"2026-10-10T10:00:00.000Z","id":"1; drop table"}`).toString('base64url')]) {
      expect(() => decodeCursor(bad)).toThrow(expect.objectContaining({ statusCode: 400 }));
    }
  });
});

describe('mergePage', () => {
  it('merges two newest-first lists into one page and points the cursor at the last item', () => {
    const txs = [row(5, '2026-10-10T10:00:00Z'), row(3, '2026-10-08T10:00:00Z'), row(1, '2026-10-01T10:00:00Z')];
    const holds = [row(4, '2026-10-09T10:00:00Z'), row(2, '2026-10-05T10:00:00Z')];
    const page = mergePage(txs, holds, 3);
    expect(page.items.map((r) => r.id)).toEqual([id(5), id(4), id(3)]);
    expect(decodeCursor(page.nextCursor!)).toEqual({ at: '2026-10-08T10:00:00.000Z', id: id(3) });
  });
  it('breaks ties on id descending, like the SQL ORDER BY', () => {
    const same = '2026-10-10T10:00:00Z';
    expect(mergePage([row(1, same)], [row(2, same)], 5).items.map((r) => r.id)).toEqual([id(2), id(1)]);
  });
  it('has no next cursor on the last page', () => {
    expect(mergePage([row(1, '2026-10-10T10:00:00Z')], [], 3).nextCursor).toBeNull();
  });
});

describe('guards', () => {
  it('hard delete only for rows already in the Trash', () => {
    expect(() => assertInTrash(undefined)).toThrow(expect.objectContaining({ statusCode: 404 }));
    expect(() => assertInTrash({ deletedAt: null })).toThrow(expect.objectContaining({ statusCode: 409, code: 'NOT_IN_TRASH' }));
    expect(() => assertInTrash({ deletedAt: new Date() })).not.toThrow();
  });
  it('a hold cannot come back while its transaction is in the Trash', () => {
    expect(() => assertParentLive(new Date())).toThrow(expect.objectContaining({ statusCode: 409, code: 'PARENT_DELETED' }));
    expect(() => assertParentLive(null)).not.toThrow();
  });
});
```

- [ ] **Step 2: Run to see it fail**

Run: `cd backend && npx vitest run src/trash/rules.test.ts`
Expected: FAIL — "Cannot find module './rules'".

- [ ] **Step 3: Implement**

`backend/src/trash/rules.ts`:

```ts
import { BadRequestError, ConflictError, NotFoundError } from '../utils/errors';

/** Keyset position in the Trash list: everything strictly older than (at, id). */
export interface TrashCursor {
  at: string;
  id: string;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function encodeCursor(c: TrashCursor): string {
  return Buffer.from(JSON.stringify(c)).toString('base64url');
}

/** The cursor comes from the client, so it is validated like any other input. */
export function decodeCursor(s: string | undefined): TrashCursor | null {
  if (!s) return null;
  try {
    const c = JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));
    if (typeof c?.at === 'string' && typeof c?.id === 'string' && UUID.test(c.id) && !Number.isNaN(Date.parse(c.at))) {
      return { at: new Date(c.at).toISOString(), id: c.id.toLowerCase() };
    }
  } catch {
    // fall through
  }
  throw new BadRequestError('Invalid cursor', 'BAD_CURSOR');
}

/** Newest first, ties on id descending — must match `ORDER BY deleted_at DESC, id DESC`. */
function newerFirst(x: { id: string; deletedAt: Date }, y: { id: string; deletedAt: Date }) {
  return y.deletedAt.getTime() - x.deletedAt.getTime() || (y.id < x.id ? -1 : y.id > x.id ? 1 : 0);
}

/** Each input holds up to limit+1 rows of one kind, already newest first. */
export function mergePage<T extends { id: string; deletedAt: Date }>(a: T[], b: T[], limit: number) {
  const all = [...a, ...b].sort(newerFirst);
  const items = all.slice(0, limit);
  const last = items[items.length - 1];
  const nextCursor = all.length > limit && last ? encodeCursor({ at: last.deletedAt.toISOString(), id: last.id }) : null;
  return { items, nextCursor };
}

/** Delete-forever works only on rows already in the Trash: one stolen call can't erase live data. */
export function assertInTrash(row: { deletedAt: Date | null } | undefined): void {
  if (!row) throw new NotFoundError('Not found');
  if (!row.deletedAt) throw new ConflictError('Move it to the Trash first', 'NOT_IN_TRASH');
}

export function assertParentLive(parentDeletedAt: Date | null): void {
  if (parentDeletedAt) throw new ConflictError('Restore its transaction instead', 'PARENT_DELETED');
}
```

- [ ] **Step 4: Run to see it pass**

Run: `cd backend && npx vitest run src/trash/rules.test.ts` → Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/trash
git commit -m "feat(trash): cursor, page merge and trash guards"
```

---

### Task 3: Soft delete and restore for transactions and holds

**Files:**
- Modify: `backend/src/services/transaction.service.ts` (`delete`, new `restore`)
- Modify: `backend/src/services/hold.service.ts` (`create` check, `delete`, new `restore`)
- Modify: `backend/src/app/api/transactions/[id]/route.ts` (DELETE response)
- Create: `backend/src/app/api/transactions/[id]/restore/route.ts`
- Create: `backend/src/app/api/holds/[id]/restore/route.ts`

**Interfaces:**
- Consumes: `liveTx`, `liveHold` (Task 1); `assertParentLive` (Task 2).
- Produces:
  - `transactionService.delete(userId, id): Promise<{ holdIds: string[] }>`
  - `transactionService.restore(userId, id): Promise<{ transaction: Transaction; holds: Hold[] }>`
  - `holdService.delete(userId, id): Promise<void>`; `holdService.restore(userId, id): Promise<Hold>`
  - HTTP: `DELETE /api/transactions/:id` → 200 `{ data: { holdIds } }`; `POST /api/transactions/:id/restore` → 200 `{ data: { transaction, holds } }`; `DELETE /api/holds/:id` → 204; `POST /api/holds/:id/restore` → 200 `{ data: hold }`.

There are no DB integration tests in this repo (tests mock `db`); the decisions live in Task 2's tested guards, and the SQL here is verified by `tsc`, the guard test, and the manual checklist in Task 8.

- [ ] **Step 1: Rewrite `transactionService.delete` and add `restore`**

In `backend/src/services/transaction.service.ts`, change imports:

```ts
import { eq, and, desc, gte, lte, sql } from 'drizzle-orm';
import { db } from '../db/index';
import { holds, transactions } from '../db/schema/index';
import { liveHold, liveTx } from '../lib/live';
```

(`liveTx` is already used by `getAll`/`getById`/`update` from Task 1.)

Replace the `delete` method with:

```ts
  /**
   * Move a transaction to the Trash, together with its live holds, in one DB transaction.
   * Both get the same `deleted_at`, which is how `restore` knows which holds went with it.
   * Already in the Trash → no-op (a double tap must not error).
   */
  async delete(userId: string, transactionId: string): Promise<{ holdIds: string[] }> {
    return db.transaction(async (tx) => {
      const mine = and(eq(transactions.id, transactionId), eq(transactions.userId, userId));
      const [row] = await tx.select({ deletedAt: transactions.deletedAt }).from(transactions).where(mine);
      if (!row) throw new NotFoundError('Transaction not found');
      if (row.deletedAt) return { holdIds: [] };

      const now = new Date();
      await tx.update(transactions).set({ deletedAt: now }).where(mine);
      const moved = await tx
        .update(holds)
        .set({ deletedAt: now })
        .where(and(eq(holds.transactionId, transactionId), eq(holds.userId, userId), liveHold))
        .returning({ id: holds.id });
      return { holdIds: moved.map((h) => h.id) };
    });
  }

  /** Bring a transaction back, plus only the holds that were deleted WITH it (same deleted_at). */
  async restore(userId: string, transactionId: string) {
    return db.transaction(async (tx) => {
      const mine = and(eq(transactions.id, transactionId), eq(transactions.userId, userId));
      // trash-aware: this read must see deleted rows
      const [row] = await tx.select().from(transactions).where(mine);
      if (!row) throw new NotFoundError('Transaction not found');
      if (!row.deletedAt) return { transaction: row, holds: [] };

      // Compared in SQL against the parent's own value, so no timestamp round-trip through JS.
      const restoredHolds = await tx
        .update(holds)
        .set({ deletedAt: null })
        .where(
          and(
            eq(holds.transactionId, transactionId),
            eq(holds.userId, userId),
            sql`${holds.deletedAt} = (SELECT deleted_at FROM transactions WHERE id = ${transactionId})`,
          ),
        )
        .returning();
      const [transaction] = await tx.update(transactions).set({ deletedAt: null, updatedAt: new Date() }).where(mine).returning();
      return { transaction, holds: restoredHolds };
    });
  }
```

- [ ] **Step 2: Rewrite `holdService.delete`, add `restore`, harden `create`**

In `backend/src/services/hold.service.ts`:

```ts
import { eq, and, desc } from 'drizzle-orm';
import { db } from '../db/index';
import { holds, transactions } from '../db/schema/index';
import { liveHold } from '../lib/live';
import { assertParentLive } from '../trash/rules';
import { transactionService } from './transaction.service';
import type { CreateHoldInput, UpdateHoldInput, HoldQuery } from '../types/hold.types';
import { NotFoundError } from '../utils/errors';
```

At the top of `create`, before the insert:

```ts
    // The transaction must be the user's own and not in the Trash (404 otherwise).
    await transactionService.getById(userId, data.transactionId);
```

Replace `delete` and add `restore`:

```ts
  /** Move one hold to the Trash. Already there → no-op. */
  async delete(userId: string, holdId: string) {
    const mine = and(eq(holds.id, holdId), eq(holds.userId, userId));
    const [row] = await db.select({ deletedAt: holds.deletedAt }).from(holds).where(mine);
    if (!row) throw new NotFoundError('Hold not found');
    if (row.deletedAt) return;
    await db.update(holds).set({ deletedAt: new Date() }).where(mine);
  }

  /** Bring one hold back. Refused while its transaction is in the Trash (restore that instead). */
  async restore(userId: string, holdId: string) {
    const mine = and(eq(holds.id, holdId), eq(holds.userId, userId));
    const [row] = await db
      .select({ hold: holds, parentDeletedAt: transactions.deletedAt })
      .from(holds)
      .innerJoin(transactions, eq(transactions.id, holds.transactionId))
      .where(mine);
    if (!row) throw new NotFoundError('Hold not found');
    if (!row.hold.deletedAt) return row.hold;
    assertParentLive(row.parentDeletedAt);
    const [hold] = await db.update(holds).set({ deletedAt: null, updatedAt: new Date() }).where(mine).returning();
    return hold;
  }
```

Note: `liveHold` is still used by `getAll`/`getById`/`update` from Task 1.

- [ ] **Step 3: Routes**

`backend/src/app/api/transactions/[id]/route.ts` — DELETE becomes:

```ts
export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    return success(await transactionService.delete(userId, id));
  },
  { auth: 'required' }
);
```

Remove `noContent` from the import if it is no longer used in that file.

`backend/src/app/api/transactions/[id]/restore/route.ts`:

```ts
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { transactionService } from '@/services/transaction.service';
import { transactionIdSchema } from '@/types/transaction.types';

export const POST = withApi(
  async (_request, { userId, params }) => {
    const { id } = transactionIdSchema.parse(await params);
    return success(await transactionService.restore(userId, id));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
```

`backend/src/app/api/holds/[id]/restore/route.ts`:

```ts
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { holdService } from '@/services/hold.service';
import { holdIdSchema } from '@/types/hold.types';

export const POST = withApi(
  async (_request, { userId, params }) => {
    const { id } = holdIdSchema.parse(await params);
    return success(await holdService.restore(userId, id));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
```

(`DELETE /api/holds/:id` route is unchanged — it already calls `holdService.delete` and returns 204.)

- [ ] **Step 4: Verify**

Run: `cd backend && npx tsc --noEmit -p .` → no errors
Run: `cd backend && npm test` → all pass (including the guard)
Run: `cd backend && npm run lint` → no new errors

- [ ] **Step 5: Commit**

```bash
git add backend/src/services backend/src/app/api/transactions backend/src/app/api/holds
git commit -m "feat(trash): soft delete and restore for transactions and holds"
```

---

### Task 4: Trash API (list, delete forever, empty) + help entry

**Files:**
- Create: `backend/src/types/trash.types.ts`
- Create: `backend/src/trash/service.ts`
- Create: `backend/src/app/api/trash/route.ts`
- Create: `backend/src/app/api/trash/[kind]/[id]/route.ts`
- Modify: `backend/src/assistant/knowledge.ts` (`HELP_ENTRIES`)
- Test: `backend/src/trash/rules.test.ts` (add schema tests)

**Interfaces:**
- Consumes: `decodeCursor`, `mergePage`, `assertInTrash` (Task 2).
- Produces (HTTP, consumed by Android in Task 5):
  - `GET /api/trash?cursor=&limit=` → `{ data: { items: TrashItem[], nextCursor: string | null } }` where
    `TrashItem = { kind: 'transaction', id, deletedAt, transaction, holds: Hold[] } | { kind: 'hold', id, deletedAt, hold }`
  - `DELETE /api/trash/:kind/:id` → 204
  - `DELETE /api/trash` → `{ data: { deleted: number } }`

- [ ] **Step 1: Write failing schema tests**

Append to `backend/src/trash/rules.test.ts`:

```ts
import { trashQuerySchema, trashTargetSchema } from '../types/trash.types';

describe('trash input', () => {
  it('limits page size and defaults it', () => {
    expect(trashQuerySchema.parse({}).limit).toBe(30);
    expect(trashQuerySchema.parse({ limit: '10' }).limit).toBe(10);
    expect(trashQuerySchema.safeParse({ limit: '500' }).success).toBe(false);
  });
  it('accepts only known kinds and uuids', () => {
    expect(trashTargetSchema.safeParse({ kind: 'transaction', id: id(1) }).success).toBe(true);
    expect(trashTargetSchema.safeParse({ kind: 'user', id: id(1) }).success).toBe(false);
    expect(trashTargetSchema.safeParse({ kind: 'hold', id: '1 OR 1=1' }).success).toBe(false);
  });
});
```

Run: `cd backend && npx vitest run src/trash/rules.test.ts` → Expected: FAIL (module not found).

- [ ] **Step 2: Schemas**

`backend/src/types/trash.types.ts`:

```ts
import { z } from 'zod';

export const trashQuerySchema = z.object({
  cursor: z.string().max(300).optional(),
  limit: z.coerce.number().int().min(1).max(50).default(30),
});

export const trashTargetSchema = z.object({
  kind: z.enum(['transaction', 'hold']),
  id: z.string().uuid(),
});
export type TrashKind = z.infer<typeof trashTargetSchema>['kind'];
```

Run the test → Expected: PASS.

- [ ] **Step 3: Service**

`backend/src/trash/service.ts`:

```ts
import { and, desc, eq, inArray, isNotNull, isNull, sql, type SQL } from 'drizzle-orm';
import { db } from '../db/index';
import { holds, transactions, type Hold, type Transaction } from '../db/schema/index';
import type { TrashKind } from '../types/trash.types';
import { assertInTrash, decodeCursor, mergePage, type TrashCursor } from './rules';

export type TrashItem =
  | { kind: 'transaction'; id: string; deletedAt: Date; transaction: Transaction; holds: Hold[] }
  | { kind: 'hold'; id: string; deletedAt: Date; hold: Hold };

/** (deleted_at, id) strictly older than the cursor — same order as the ORDER BY below. */
function olderThan(c: TrashCursor | null, deletedAt: typeof transactions.deletedAt | typeof holds.deletedAt, id: typeof transactions.id | typeof holds.id): SQL | undefined {
  return c ? sql`(${deletedAt}, ${id}) < (${c.at}::timestamp, ${c.id}::uuid)` : undefined;
}

export const trashService = {
  /** Deleted transactions (with their deleted holds nested) + holds deleted on their own, newest first. */
  async list(userId: string, cursor: string | undefined, limit: number) {
    const c = decodeCursor(cursor);

    const txRows = await db
      .select()
      .from(transactions)
      .where(and(eq(transactions.userId, userId), isNotNull(transactions.deletedAt), olderThan(c, transactions.deletedAt, transactions.id)))
      .orderBy(desc(transactions.deletedAt), desc(transactions.id))
      .limit(limit + 1);

    const holdRows = await db
      .select({ hold: holds })
      .from(holds)
      .innerJoin(transactions, eq(transactions.id, holds.transactionId))
      .where(and(eq(holds.userId, userId), isNotNull(holds.deletedAt), isNull(transactions.deletedAt), olderThan(c, holds.deletedAt, holds.id)))
      .orderBy(desc(holds.deletedAt), desc(holds.id))
      .limit(limit + 1);

    const page = mergePage<TrashItem>(
      txRows.map((t) => ({ kind: 'transaction', id: t.id, deletedAt: t.deletedAt!, transaction: t, holds: [] })),
      holdRows.map((r) => ({ kind: 'hold', id: r.hold.id, deletedAt: r.hold.deletedAt!, hold: r.hold })),
      limit,
    );

    const txIds = page.items.filter((i) => i.kind === 'transaction').map((i) => i.id);
    if (txIds.length) {
      const nested = await db
        .select()
        .from(holds)
        .where(and(eq(holds.userId, userId), inArray(holds.transactionId, txIds), isNotNull(holds.deletedAt)));
      for (const item of page.items) {
        if (item.kind === 'transaction') item.holds = nested.filter((h) => h.transactionId === item.id);
      }
    }
    return page;
  },

  /** Irreversible. Only for rows already in the Trash (see assertInTrash). */
  async deleteForever(userId: string, kind: TrashKind, id: string) {
    if (kind === 'transaction') {
      const mine = and(eq(transactions.id, id), eq(transactions.userId, userId));
      const [row] = await db.select({ deletedAt: transactions.deletedAt }).from(transactions).where(mine);
      assertInTrash(row);
      // FK cascade removes its holds.
      await db.delete(transactions).where(and(mine, isNotNull(transactions.deletedAt)));
    } else {
      const mine = and(eq(holds.id, id), eq(holds.userId, userId));
      const [row] = await db.select({ deletedAt: holds.deletedAt }).from(holds).where(mine);
      assertInTrash(row);
      await db.delete(holds).where(and(mine, isNotNull(holds.deletedAt)));
    }
  },

  async empty(userId: string) {
    return db.transaction(async (tx) => {
      const h = await tx.delete(holds).where(and(eq(holds.userId, userId), isNotNull(holds.deletedAt))).returning({ id: holds.id });
      const t = await tx.delete(transactions).where(and(eq(transactions.userId, userId), isNotNull(transactions.deletedAt))).returning({ id: transactions.id });
      return { deleted: h.length + t.length };
    });
  },
};
```

- [ ] **Step 4: Routes**

`backend/src/app/api/trash/route.ts`:

```ts
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { trashService } from '@/trash/service';
import { trashQuerySchema } from '@/types/trash.types';

export const GET = withApi(
  async (request, { userId }) => {
    const { cursor, limit } = trashQuerySchema.parse(Object.fromEntries(request.nextUrl.searchParams));
    return success(await trashService.list(userId, cursor, limit));
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId }) => success(await trashService.empty(userId)),
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
```

`backend/src/app/api/trash/[kind]/[id]/route.ts`:

```ts
import { withApi, corsPreflight } from '@/lib/api-handler';
import { noContent } from '@/lib/response';
import { trashService } from '@/trash/service';
import { trashTargetSchema } from '@/types/trash.types';

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { kind, id } = trashTargetSchema.parse(await params);
    await trashService.deleteForever(userId, kind, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
```

- [ ] **Step 5: Help entry**

In `backend/src/assistant/knowledge.ts`, add to `HELP_ENTRIES` (after the `add_transaction` entry):

```ts
  {
    id: 'trash',
    title: 'Deleted items and the Trash',
    screen: 'profile',
    text:
      'Deleting an expense, income or hold moves it to the Trash instead of erasing it. Right after deleting, tap Undo to bring it back. Later, open Profile → Settings → Trash: every deleted item is listed with Restore and Delete forever. Items in the Trash do not count in your balance, plans, analytics or exports. They stay there until you delete them forever or tap Empty Trash, which cannot be undone.',
  },
```

- [ ] **Step 6: Verify and commit**

Run: `cd backend && npx tsc --noEmit -p .` → no errors
Run: `cd backend && npm test` → all pass (guard included: trash service reads mention `deletedAt`)
Run: `cd backend && npm run lint`

```bash
git add backend/src/types/trash.types.ts backend/src/trash backend/src/app/api/trash backend/src/assistant/knowledge.ts
git commit -m "feat(trash): list, delete forever and empty Trash endpoints"
```

---

### Task 5: Android data layer

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/data/remote/model/AppModels.kt`
- Modify: `app/src/main/java/com/example/spendsync/data/remote/AppApiService.kt`
- Modify: `app/src/main/java/com/example/spendsync/data/repository/FinanceRepository.kt`
- Modify: `app/src/main/java/com/example/spendsync/notifications/HoldReminderWorker.kt`

**Interfaces:**
- Consumes: HTTP shapes from Tasks 3–4.
- Produces:
  - DTOs `DeleteTransactionResult(holdIds: List<String>)`, `RestoredTransactionDto(transaction: TransactionDto, holds: List<HoldDto>)`, `TrashItemDto(kind, id, deletedAt, transaction: TransactionDto?, holds: List<HoldDto>?, hold: HoldDto?)`, `TrashPageDto(items: List<TrashItemDto>, nextCursor: String?)`, `TrashEmptiedDto(deleted: Int)`
  - `FinanceRepository.deleteTransaction(id): AuthResult<List<String>>` (hold ids moved with it)
  - `FinanceRepository.restoreTransaction(id): AuthResult<RestoredTransactionDto>`
  - `FinanceRepository.restoreHold(id): AuthResult<HoldDto>`
  - `FinanceRepository.getTrash(cursor: String?): AuthResult<TrashPageDto>`
  - `FinanceRepository.deleteForever(kind: String, id: String): AuthResult<Unit>`
  - `FinanceRepository.emptyTrash(): AuthResult<Int>`
  - `HoldReminderWorker.scheduleFor(context: Context, hold: HoldDto)`

- [ ] **Step 1: DTOs** — append to `AppModels.kt`:

```kotlin
// ── Trash ─────────────────────────────────────────────────────────────────────

data class DeleteTransactionResult(
    @SerializedName("holdIds") val holdIds: List<String>,
)

data class RestoredTransactionDto(
    @SerializedName("transaction") val transaction: TransactionDto,
    @SerializedName("holds")       val holds: List<HoldDto>,
)

/** kind "transaction" → [transaction] + [holds]; kind "hold" → [hold]. */
data class TrashItemDto(
    @SerializedName("kind")        val kind: String,
    @SerializedName("id")          val id: String,
    @SerializedName("deletedAt")   val deletedAt: String,
    @SerializedName("transaction") val transaction: TransactionDto?,
    @SerializedName("holds")       val holds: List<HoldDto>?,
    @SerializedName("hold")        val hold: HoldDto?,
)

data class TrashPageDto(
    @SerializedName("items")      val items: List<TrashItemDto>,
    @SerializedName("nextCursor") val nextCursor: String?,
)

data class TrashEmptiedDto(
    @SerializedName("deleted") val deleted: Int,
)
```

- [ ] **Step 2: API** — in `AppApiService.kt` change `deleteTransaction`'s return type and add the rest (next to the existing transaction/hold blocks):

```kotlin
    @DELETE("api/transactions/{id}")
    suspend fun deleteTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<DeleteTransactionResult>>

    @POST("api/transactions/{id}/restore")
    suspend fun restoreTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<RestoredTransactionDto>>

    @POST("api/holds/{id}/restore")
    suspend fun restoreHold(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<HoldDto>>

    // ── Trash ─────────────────────────────────────────────────────────────────

    @GET("api/trash")
    suspend fun getTrash(
        @Header("Authorization") token: String,
        @Query("cursor") cursor: String?
    ): Response<SuccessResponse<TrashPageDto>>

    @DELETE("api/trash/{kind}/{id}")
    suspend fun deleteForever(
        @Header("Authorization") token: String,
        @Path("kind") kind: String,
        @Path("id") id: String
    ): Response<Unit>

    @DELETE("api/trash")
    suspend fun emptyTrash(
        @Header("Authorization") token: String
    ): Response<SuccessResponse<TrashEmptiedDto>>
```

Add the new DTO imports if `AppApiService.kt` imports models one by one.

- [ ] **Step 3: Repository** — in `FinanceRepository.kt` replace `deleteTransaction` and add the rest, in the file's existing style:

```kotlin
    /** Moves it to the Trash. Returns the ids of holds that went with it (their reminders must stop). */
    suspend fun deleteTransaction(id: String): AuthResult<List<String>> {
        return try {
            val response = api.deleteTransaction(getAuthHeader(), id)
            if (response.isSuccessful) {
                // "holds" too — the backend moves the linked holds to the Trash with it.
                cacheInvalidate("transactions", "dashboard", "holds", "plan")
                AuthResult.Success(response.body()?.data?.holdIds.orEmpty())
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun restoreTransaction(id: String): AuthResult<RestoredTransactionDto> {
        return try {
            val response = api.restoreTransaction(getAuthHeader(), id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheInvalidate("transactions", "dashboard", "holds", "plan")
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun restoreHold(id: String): AuthResult<HoldDto> {
        return try {
            val response = api.restoreHold(getAuthHeader(), id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheInvalidate("holds")
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Never cached: the Trash changes from several screens. */
    suspend fun getTrash(cursor: String?): AuthResult<TrashPageDto> {
        return try {
            val response = api.getTrash(getAuthHeader(), cursor)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteForever(kind: String, id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteForever(getAuthHeader(), kind, id)
            if (response.isSuccessful) AuthResult.Success(Unit)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun emptyTrash(): AuthResult<Int> {
        return try {
            val response = api.emptyTrash(getAuthHeader())
            if (response.isSuccessful) AuthResult.Success(response.body()?.data?.deleted ?: 0)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }
```

Also update the doc comment on `deleteHold` to "Moves the hold to the Trash." (body unchanged).

- [ ] **Step 4: Reminder helper** — in `HoldReminderWorker`'s `companion object`, after `cancel`:

```kotlin
        /** Re-arms the reminder of a hold that came back from the Trash (pending only). */
        fun scheduleFor(context: Context, hold: com.example.spendsync.data.remote.model.HoldDto) {
            if (hold.status != "pending") return
            val due = runCatching { java.time.ZonedDateTime.parse(hold.expectedReturnDate).toLocalDate() }.getOrNull() ?: return
            schedule(context, hold.id, hold.personName, hold.amount.toDoubleOrNull() ?: 0.0, hold.direction, due)
        }
```

- [ ] **Step 5: Build** (HomeScreen still compiles: it only checks `res is AuthResult.Success`)

Run: `./gradlew assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/spendsync/data app/src/main/java/com/example/spendsync/notifications/HoldReminderWorker.kt
git commit -m "feat(trash): Android API, repository and reminder helper"
```

---

### Task 6: Undo bar + instant delete on Home and Holds

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/components/ToastHost.kt`
- Modify: `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt` (~lines 350, 379, 550-570)
- Modify: `app/src/main/java/com/example/spendsync/ui/home/TransactionDialogs.kt` (remove `DeleteTransactionDialog`)
- Modify: `app/src/main/java/com/example/spendsync/ui/holds/HoldDetailScreen.kt` (~lines 132-145, 195-205)
- Modify: `app/src/main/res/values{,-hi,-es,-fr,-de}/strings.xml` (2 keys)

**Interfaces:**
- Consumes: Task 5 repository methods, `HoldReminderWorker.scheduleFor`.
- Produces: `ToastMessage(message, isError, id, actionLabel: String? = null, onAction: (() -> Unit)? = null)` — reusable Undo bar; strings `trash_moved`, `trash_undo`.

- [ ] **Step 1: Strings** — add to each file:

| key | en | hi | es | fr | de |
|---|---|---|---|---|---|
| `trash_moved` | Moved to Trash | ट्रैश में भेजा गया | Movido a la papelera | Déplacé dans la corbeille | In den Papierkorb verschoben |
| `trash_undo` | Undo | वापस लें | Deshacer | Annuler | Rückgängig |

e.g. `<string name="trash_moved">Moved to Trash</string>`.

- [ ] **Step 2: Undo action on the toast** — `ToastHost.kt`:

```kotlin
data class ToastMessage(
    val message: String,
    val isError: Boolean = true,
    val id: Long = System.currentTimeMillis(), // unique ID so same text re-triggers
    /** Optional action (e.g. Undo). With an action the toast stays 5 s. */
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
```

In `ToastHost`, replace `delay(autoDismissMs)` with `delay(if (toast.onAction != null) 5_000L else autoDismissMs)`, and render:

```kotlin
            current?.let { msg ->
                ToastBanner(
                    message = msg.message,
                    isError = msg.isError,
                    actionLabel = msg.actionLabel,
                    onAction = msg.onAction?.let { action -> { action(); visible = false } },
                )
            }
```

`ToastBanner` gets `actionLabel: String? = null, onAction: (() -> Unit)? = null` and, after the message `Text`:

```kotlin
        if (actionLabel != null && onAction != null) {
            Text(
                text       = actionLabel,
                color      = NeutralWhite,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
```

(Add `import androidx.compose.foundation.clickable`. A plain clickable label, not `AppButton`, because the banner sits on a coloured background where `AppButton`'s theme colours don't read.)

- [ ] **Step 3: Home — delete instantly with Undo** — in `HomeScreen.kt`:
  - Remove `var transactionToDelete …` (~line 350) and the whole `transactionToDelete?.let { … DeleteTransactionDialog … }` block (~lines 550-570).
  - Add `val context = androidx.compose.ui.platform.LocalContext.current` near the other `remember`s if not present.
  - Replace line 379 (`val onDeleteTx … = { transactionToDelete = it }`) with:

```kotlin
    fun restoreTx(tx: TransactionDto) {
        scope.launch {
            when (val r = financeRepository.restoreTransaction(tx.id)) {
                is AuthResult.Success -> {
                    monthlyTransactions = monthlyTransactions.filter { it.id != tx.id } + r.data.transaction
                    r.data.holds.forEach { HoldReminderWorker.scheduleFor(context, it) }
                    balanceRefreshKey++
                }
                is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
            }
        }
    }

    // Instant: the row leaves now, the server moves it to the Trash, Undo brings it back.
    val onDeleteTx: (TransactionDto) -> Unit = { tx ->
        monthlyTransactions = monthlyTransactions.filter { it.id != tx.id }
        scope.launch {
            when (val res = financeRepository.deleteTransaction(tx.id)) {
                is AuthResult.Success -> {
                    res.data.forEach { HoldReminderWorker.cancel(context, it) }
                    balanceRefreshKey++
                    toast = ToastMessage(tr(R.string.trash_moved), isError = false, actionLabel = tr(R.string.trash_undo), onAction = { restoreTx(tx) })
                }
                is AuthResult.Error -> {
                    if (monthlyTransactions.none { it.id == tx.id }) monthlyTransactions = monthlyTransactions + tx
                    toast = ToastMessage(res.message, isError = true)
                }
            }
        }
    }
```

  Add `import com.example.spendsync.notifications.HoldReminderWorker`. The list is sorted at render (`sortedByDescending { it.createdAt }`), so appending is enough.
  - In `TransactionDialogs.kt` delete `DeleteTransactionDialog` (now unused). Leave its strings.

- [ ] **Step 4: Holds — delete instantly with Undo** — in `HoldDetailScreen.kt`:
  - Remove `holdToDelete` and `deleting` state and the `holdToDelete?.let { AppConfirmDialog … }` block.
  - `HoldCard(… onDelete = { deleteHold(hold) } …)`.
  - Replace `fun deleteHold`:

```kotlin
    fun restoreHold(hold: HoldDto) {
        scope.launch {
            when (val r = financeRepository.restoreHold(hold.id)) {
                is AuthResult.Success -> {
                    HoldReminderWorker.scheduleFor(context, r.data)
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
            }
        }
    }

    fun deleteHold(hold: HoldDto) {
        scope.launch {
            when (val res = financeRepository.deleteHold(hold.id)) {
                is AuthResult.Success -> {
                    HoldReminderWorker.cancel(context, hold.id)
                    onHoldsChanged()
                    toast = ToastMessage(tr(R.string.trash_moved), isError = false, actionLabel = tr(R.string.trash_undo), onAction = { restoreHold(hold) })
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }
```

- [ ] **Step 5: Build and test**

Run: `./gradlew assembleDebug` → BUILD SUCCESSFUL (fix any now-unused imports such as `AppConfirmDialog` in `HoldDetailScreen.kt` only if the compiler warns they are unused and nothing else uses them)
Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.I18nResourcesTest"` → PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main
git commit -m "feat(trash): instant delete with Undo on Home and Holds"
```

---

### Task 7: Settings → Trash page

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/settings/SettingsScreens.kt` (`SettingsActions` ~line 138, `SettingsPage` enum ~line 161, page `when` ~line 294, new `TrashPage`)
- Modify: `app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt` (~line 345, `SettingsActions(...)`)
- Modify: `app/src/main/java/com/example/spendsync/ui/search/AppSearchIndex.kt` (~line 75)
- Modify: `app/src/main/res/values{,-hi,-es,-fr,-de}/strings.xml`

**Interfaces:**
- Consumes: Task 5 repository methods + DTOs; `HoldReminderWorker.scheduleFor`; `MaskableAmountText`, `AmountVisibilityState`.
- Produces: `SettingsPage.Trash`; new `SettingsActions` fields `loadTrash`, `restoreTrashItem`, `deleteTrashItem`, `emptyTrash`, `amountVisibility`.

- [ ] **Step 1: Strings** — add to each file:

| key | en | hi | es | fr | de |
|---|---|---|---|---|---|
| `trash_title` | Trash | ट्रैश | Papelera | Corbeille | Papierkorb |
| `trash_summary` | Deleted transactions and holds | हटाए गए लेन-देन और होल्ड | Transacciones y retenciones eliminadas | Transactions et retenues supprimées | Gelöschte Buchungen und Rückforderungen |
| `trash_restore` | Restore | वापस लाएँ | Restaurar | Restaurer | Wiederherstellen |
| `trash_delete_forever` | Delete forever | हमेशा के लिए हटाएँ | Eliminar para siempre | Supprimer définitivement | Endgültig löschen |
| `trash_delete_forever_title` | Delete forever? | हमेशा के लिए हटाएँ? | ¿Eliminar para siempre? | Supprimer définitivement ? | Endgültig löschen? |
| `trash_cannot_undo` | This can\'t be undone. | इसे वापस नहीं लिया जा सकता। | No se puede deshacer. | Action irréversible. | Das kann nicht rückgängig gemacht werden. |
| `trash_empty` | Empty Trash | ट्रैश खाली करें | Vaciar papelera | Vider la corbeille | Papierkorb leeren |
| `trash_empty_title` | Empty Trash? | ट्रैश खाली करें? | ¿Vaciar la papelera? | Vider la corbeille ? | Papierkorb leeren? |
| `trash_empty_body` | Everything in the Trash will be deleted forever. This can\'t be undone. | ट्रैश की हर चीज़ हमेशा के लिए हट जाएगी। इसे वापस नहीं लिया जा सकता। | Todo lo de la papelera se eliminará para siempre. No se puede deshacer. | Tout le contenu de la corbeille sera supprimé définitivement. Action irréversible. | Alles im Papierkorb wird endgültig gelöscht. Das kann nicht rückgängig gemacht werden. |
| `trash_empty_state` | Trash is empty. Deleted items stay here until you restore or delete them. | ट्रैश खाली है। हटाई गई चीज़ें तब तक यहाँ रहती हैं जब तक आप उन्हें वापस न लाएँ या हटा न दें। | La papelera está vacía. Lo eliminado se queda aquí hasta que lo restaures o lo borres. | La corbeille est vide. Les éléments supprimés restent ici jusqu\'à ce que vous les restauriez ou les supprimiez. | Der Papierkorb ist leer. Gelöschtes bleibt hier, bis du es wiederherstellst oder löschst. |
| `trash_failed` | Couldn\'t load the Trash. Try again. | ट्रैश लोड नहीं हो सका। फिर से कोशिश करें। | No se pudo cargar la papelera. Inténtalo de nuevo. | Impossible de charger la corbeille. Réessayez. | Papierkorb konnte nicht geladen werden. Versuche es erneut. |
| `trash_action_failed` | That didn\'t work. Try again. | यह नहीं हो सका। फिर से कोशिश करें। | No funcionó. Inténtalo de nuevo. | Cela n\'a pas fonctionné. Réessayez. | Das hat nicht geklappt. Versuche es erneut. |
| `trash_load_more` | Load more | और दिखाएँ | Cargar más | Charger plus | Mehr laden |
| `trash_deleted_on` | Deleted %1$s | %1$s को हटाया | Eliminado el %1$s | Supprimé le %1$s | Gelöscht am %1$s |
| `trash_with_hold` | Includes hold with %1$s | %1$s के साथ होल्ड शामिल | Incluye retención con %1$s | Inclut la retenue avec %1$s | Inklusive Rückforderung bei %1$s |
| `trash_hold_label` | Hold · %1$s | होल्ड · %1$s | Retención · %1$s | Retenue · %1$s | Rückforderung · %1$s |

(Escape apostrophes as `\'` in every file, as shown.)

- [ ] **Step 2: Actions + page enum** — in `SettingsScreens.kt`:

Add to `SettingsActions` (after `closeReport`):

```kotlin
    val loadTrash: suspend (cursor: String?) -> com.example.spendsync.data.remote.model.TrashPageDto?,
    val restoreTrashItem: suspend (com.example.spendsync.data.remote.model.TrashItemDto) -> Boolean,
    val deleteTrashItem: suspend (com.example.spendsync.data.remote.model.TrashItemDto) -> Boolean,
    val emptyTrash: suspend () -> Boolean,
    val amountVisibility: com.example.spendsync.ui.shared.AmountVisibilityState,
```

Add to `SettingsPage`, before `About`:

```kotlin
    Trash(R.string.trash_title, Icons.Default.Delete, { tr(R.string.trash_summary) }),
```

(import `androidx.compose.material.icons.filled.Delete` if icons are imported individually.) In the page `when`: `SettingsPage.Trash -> TrashPage(actions)`.

- [ ] **Step 3: The page** — add below `ReportsPage` in `SettingsScreens.kt`:

```kotlin
@Composable
private fun TrashPage(a: SettingsActions) {
    val scheme = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var items by remember { mutableStateOf<List<com.example.spendsync.data.remote.model.TrashItemDto>?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var actionFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<com.example.spendsync.data.remote.model.TrashItemDto?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    suspend fun load(cursor: String?) {
        val page = a.loadTrash(cursor)
        failed = page == null && items == null
        if (page != null) {
            items = if (cursor == null) page.items else items.orEmpty() + page.items
            next = page.nextCursor
        }
    }
    fun act(block: suspend () -> Boolean) {
        scope.launch {
            busy = true
            actionFailed = !block()
            if (!actionFailed) load(null)
            busy = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load(null) }

    val list = items
    if (!list.isNullOrEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            AppButton(tr(R.string.trash_empty), onClick = { confirmEmpty = true }, variant = ButtonVariant.Outline, size = ButtonSize.Small, enabled = !busy)
        }
    }
    if (actionFailed) Text(tr(R.string.trash_action_failed), color = scheme.error, modifier = Modifier.padding(horizontal = 24.dp))
    when {
        list == null && failed -> Text(tr(R.string.trash_failed), color = scheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        list == null -> Skeleton(loading = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { i -> TrashRow(trashPlaceholder(i), a, busy = true, onRestore = {}, onDelete = {}) } }
        }
        list.isEmpty() -> Text(tr(R.string.trash_empty_state), color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEachIndexed { i, item ->
                TrashRow(
                    item, a, busy, Modifier.cascadeIn(i),
                    onRestore = { act { a.restoreTrashItem(item) } },
                    onDelete = { toDelete = item },
                )
            }
            if (next != null) {
                AppButton(tr(R.string.trash_load_more), onClick = { scope.launch { load(next) } }, variant = ButtonVariant.Text, size = ButtonSize.Small, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    toDelete?.let { item ->
        com.example.spendsync.ui.components.AppConfirmDialog(
            title = tr(R.string.trash_delete_forever_title), message = tr(R.string.trash_cannot_undo),
            confirmLabel = tr(R.string.trash_delete_forever), cancelLabel = tr(R.string.cancel), destructive = true,
            onConfirm = { toDelete = null; act { a.deleteTrashItem(item) } },
            onDismiss = { toDelete = null },
        )
    }
    if (confirmEmpty) {
        com.example.spendsync.ui.components.AppConfirmDialog(
            title = tr(R.string.trash_empty_title), message = tr(R.string.trash_empty_body),
            confirmLabel = tr(R.string.trash_empty), cancelLabel = tr(R.string.cancel), destructive = true,
            onConfirm = { confirmEmpty = false; act { a.emptyTrash() } },
            onDismiss = { confirmEmpty = false },
        )
    }
}

@Composable
private fun TrashRow(
    item: com.example.spendsync.data.remote.model.TrashItemDto,
    a: SettingsActions,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tx = item.transaction
    val hold = item.hold
    val title = tx?.merchant ?: tr(R.string.trash_hold_label, hold?.personName.orEmpty())
    val amount = (tx?.amount ?: hold?.amount)?.toDoubleOrNull() ?: 0.0
    val isDebit = tx?.type == "debit"
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.weight(1f))
            com.example.spendsync.ui.shared.MaskableAmountText(
                amount = amount,
                visibility = a.amountVisibility,
                prefix = if (tx == null) "" else if (isDebit) "-" else "+",
                color = if (tx == null) scheme.onSurface else if (isDebit) expenseColor() else incomeColor(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        tx?.let { Text("${categoryLabel(it.category)} · ${it.createdAt.take(10)}", fontSize = 12.sp, color = scheme.onSurfaceVariant) }
        item.holds.orEmpty().forEach { h -> Text(tr(R.string.trash_with_hold, h.personName), fontSize = 12.sp, color = scheme.onSurfaceVariant) }
        Text(tr(R.string.trash_deleted_on, item.deletedAt.take(10)), fontSize = 11.sp, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(tr(R.string.trash_restore), onClick = onRestore, variant = ButtonVariant.Tonal, size = ButtonSize.Small, enabled = !busy)
            AppButton(tr(R.string.trash_delete_forever), onClick = onDelete, variant = ButtonVariant.Text, size = ButtonSize.Small, enabled = !busy)
        }
    }
}

/** Fake rows the skeleton turns into bones while the Trash loads. */
private fun trashPlaceholder(i: Int) = com.example.spendsync.data.remote.model.TrashItemDto(
    kind = "transaction", id = "placeholder-$i", deletedAt = "2026-01-01",
    transaction = com.example.spendsync.data.remote.model.TransactionDto(
        id = "placeholder-$i", userId = "", amount = "100.00", type = "debit", merchant = "Merchant name",
        category = "Shopping", sourceApp = null, note = null, createdAt = "2026-01-01", updatedAt = null,
    ),
    holds = emptyList(), hold = null,
)
```

Make sure `Text` in this file comes from `com.example.spendsync.ui.components` (so the skeleton turns it into bones); if the file imports material3 `Text`, switch these new usages to the components one. Import `Skeleton`, `expenseColor`, `incomeColor` from where other screens import them (`grep -rn "import .*expenseColor" app/src/main/java | head -1`).

- [ ] **Step 4: Wire it up** — in `ProfileScreen.kt` `SettingsActions(...)`, after `closeReport`:

```kotlin
        loadTrash = { cursor -> (financeRepository.getTrash(cursor) as? AuthResult.Success)?.data },
        restoreTrashItem = { item ->
            if (item.kind == "transaction") {
                val r = financeRepository.restoreTransaction(item.id)
                if (r is AuthResult.Success) r.data.holds.forEach { HoldReminderWorker.scheduleFor(context, it) }
                r is AuthResult.Success
            } else {
                val r = financeRepository.restoreHold(item.id)
                if (r is AuthResult.Success) HoldReminderWorker.scheduleFor(context, r.data)
                r is AuthResult.Success
            }
        },
        deleteTrashItem = { item -> financeRepository.deleteForever(item.kind, item.id) is AuthResult.Success },
        emptyTrash = { financeRepository.emptyTrash() is AuthResult.Success },
        amountVisibility = amountVisibility,
```

Add imports for `AuthResult` / `HoldReminderWorker` if missing. Run `grep -rn "SettingsActions(" app/src` — if any other constructor call exists (previews/tests), pass the same fields there.

- [ ] **Step 5: Search** — `AppSearchIndex.kt`, next to the Reports row:

```kotlin
    setting(SettingsPage.Trash, R.string.trash_title, "trash", "deleted", "restore", "undo", "bin", "recycle")
```

- [ ] **Step 6: Build and test**

Run: `./gradlew assembleDebug` → BUILD SUCCESSFUL
Run: `./gradlew testDebugUnitTest` → all pass (incl. `I18nResourcesTest`)

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat(trash): Settings → Trash page with restore, delete forever and empty"
```

---

### Task 8: Docs + end-to-end check (needs the user's db:push)

**Files:**
- Modify: `CLAUDE.md` (one bullet)

- [ ] **Step 1: CLAUDE.md** — under "## Backend" → "### Architecture", add:

```markdown
- **Trash / soft delete**: `transactions.deleted_at` / `holds.deleted_at`. Every read must add `liveTx` / `liveHold` (`src/lib/live.ts`); `src/lib/live.test.ts` fails if a `.from(transactions|holds)` forgets it. Deleting a transaction moves its holds with it (same `deleted_at`); `src/trash/` lists, hard-deletes (only rows already in Trash) and empties. Android: instant delete + Undo (`ToastMessage.onAction`), Settings → Trash.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: note the Trash convention"
```

- [ ] **Step 3: Hand off the schema change** — tell the user to run `cd backend && npm run db:push` themselves (adds two nullable columns + two indexes; no data loss expected — read its list before confirming), then deploy the backend **before** installing the new app (the old app's DELETE still works against the new API; the new app's restore/trash calls need the new API).

- [ ] **Step 4: Manual checklist (after the user's db:push + deploy)**

1. Delete an expense on Home → row disappears, "Moved to Trash · Undo" shows 5 s, balance updates. Tap Undo → row and balance come back.
2. Delete again, wait → Settings → Trash lists it; Planify spent and Analytics no longer include it; Restore → it is back everywhere.
3. Expense with a hold: delete → hold gone from Holds, its reminder cancelled; Trash row shows "Includes hold with …"; restore → hold back.
4. Delete a hold alone, then delete its transaction, then restore the transaction → the separately-deleted hold stays in Trash (Review Focus 2).
5. Delete the last hold of a person (screen may close) → Settings → Trash still restores it (Review Focus 5).
6. Delete forever and Empty Trash ask for confirmation and remove items; with masking on, Trash amounts over ₹1000 show ★★★★★.
7. Double-tap Delete / Undo quickly → no error toast (Review Focus 1).
