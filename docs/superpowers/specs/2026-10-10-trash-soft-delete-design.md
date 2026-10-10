# Trash / soft delete — design

Module 1 of 4 in the bills initiative (1 Trash → 2 Bills on Cloudinary → 3 Bill AI → 4 Assistant + email).
Later modules reuse the `deleted_at` convention defined here.

## Goal

Deleting an expense, income or hold must never lose data by mistake. Deleted items vanish from every
list, total, plan and export, and live in **Settings → Trash** until the user restores them or deletes
them forever.

## Decisions (agreed)

- Soft delete via a nullable `deleted_at` column on the existing tables (not an archive table, not a view).
- Items stay in Trash **until the user deletes them** — no automatic purge.
- Delete is **instant + 5 s Undo bar**; the old confirm dialog is removed. Hard delete always confirms.
- In scope: transactions (expenses + income) and holds. Out of scope: categories, budgets.
- No assistant tool restores or deletes anything (existing "no destructive tools" rule); only a help entry.

## Data

Both schema files (`src/db/schema/*.schema.ts` **and** `src/db/schema.ts`):

- `transactions.deleted_at timestamp NULL`
- `holds.deleted_at timestamp NULL`
- index `transactions_user_deleted_idx (user_id, deleted_at)`, same for `holds`.

The user applies the schema change (`npm run db:push`); the implementation never runs it.

### Cascade rule

Soft-deleting a transaction sets `deleted_at = now` on the transaction **and** on its live holds, in one
DB transaction, with the **same timestamp value**. Restoring the transaction clears `deleted_at` on the
transaction and on holds whose `deleted_at` equals the transaction's — so a hold the user had deleted
separately earlier stays in Trash. Hard delete of a transaction relies on the existing FK cascade.

## Backend

### Shared filter

`src/lib/live.ts` exports `liveTx = isNull(transactions.deletedAt)` and `liveHold = isNull(holds.deletedAt)`.
Every read adds it to its `where`. Sites today:

| File | Queries |
|---|---|
| `services/transaction.service.ts` | list, count, getById |
| `services/dashboard.service.ts` | 3 aggregates |
| `services/hold.service.ts` | list, getById |
| `planify/service.ts` | 4 queries |
| `planify/auto-classify.service.ts` | 1 |
| `export/service.ts` | transactions + holds |
| `assistant/tools.ts` | 3 |

`update` on a deleted row → 404 (`NotFoundError`), same as a missing row.

**Guard test** (`src/lib/live.test.ts`): reads every non-test `.ts` under `src/`, finds each
`.from(transactions)` / `.from(holds)` call, and fails unless the surrounding query statement references
`liveTx`/`liveHold` (or `deletedAt`). Trash service files are allow-listed. This stops a future query from
silently counting deleted money.

### Endpoints (all `withApi(..., { auth: 'required' })`, scoped by `userId`)

| Method + path | Behaviour |
|---|---|
| `DELETE /api/transactions/:id` | soft delete (+ cascade to holds). Already deleted → 204 (idempotent). Missing/other user → 404. |
| `POST /api/transactions/:id/restore` | clear `deleted_at` (+ cascaded holds). Not deleted → 200 no-op. Returns the transaction. |
| `DELETE /api/holds/:id` | soft delete the hold only. |
| `POST /api/holds/:id/restore` | 409 `PARENT_DELETED` if its transaction is in Trash (restore that instead). |
| `GET /api/trash?cursor=&limit=` | deleted transactions (with their cascaded holds nested) + separately-deleted holds whose transaction is live; ordered `deleted_at DESC, id`; keyset cursor; `limit` ≤ 50 (default 30). |
| `DELETE /api/trash/:kind/:id` | `kind ∈ {transaction, hold}`; hard delete, **only if already soft-deleted** (409 `NOT_IN_TRASH` otherwise) — a stolen token can't skip the Trash in one call. |
| `DELETE /api/trash` | hard delete everything currently in the user's Trash; returns `{ deleted: n }`. |

Code lives in `src/trash/service.ts` (+ `trash.types.ts` zod schemas); restore/soft delete stay in the
existing transaction/hold services. Routes re-export `corsPreflight as OPTIONS`.

### Security / failure notes

- Every statement filters on `user_id`; ids are validated as UUIDs by zod.
- Soft delete, restore and the cascade each run in one DB transaction — no half-deleted state.
- Repeated calls are idempotent, so Android retries are safe.
- Hard delete is the only irreversible path and needs two steps (soft first, then confirm in Trash).

## Android

- `FinanceRepository`: `deleteTransaction` (unchanged URL, now soft), `restoreTransaction`, `deleteHold`,
  `restoreHold`, `getTrash(cursor)`, `deleteForever(kind, id)`, `emptyTrash()`. DTOs in `data/remote/model`.
- **Delete flow** (`HomeScreen` and every other delete site): remove the row from local state, send the
  DELETE, show a snackbar-style bar "Moved to Trash" + **Undo** for 5 s. Undo calls restore and re-inserts
  the row. A failed DELETE puts the row back and shows the error toast. Delete confirm dialog removed.
- **Hold reminders**: when a transaction with holds, or a hold, is deleted → `HoldReminderWorker.cancel`;
  on restore → reschedule from the returned hold(s).
- **Settings → Trash** (`ui/settings/TrashPage.kt`, row in `SettingsScreens`, row in `AppSearchIndex`):
  - List rows: type icon, merchant / person, amount through the masking helpers, date, "Deleted <relative time>";
    a cascaded hold is shown as a sub-line on its transaction.
  - Per row: **Restore** (`AppButton`) and **Delete forever** (`AppConfirmDialog`).
  - Top action: **Empty Trash** (`AppConfirmDialog`, shows the count). Disabled when empty.
  - `Skeleton(loading)` placeholder rows; empty state text; paging on scroll; pull-to-refresh via
    `AppPullToRefresh`.
  - After restore/delete-forever, dashboard/Planify refresh keys are bumped so totals update.
- All strings via `tr(R.string.trash_*)` in en/hi/es/fr/de (`I18nResourcesTest` enforces).

## Assistant

`knowledge.ts` `HELP_ENTRIES` gets "Where did my deleted transaction go? / How do I restore?" pointing to
Settings → Trash. No tools.

## Tests

Backend (vitest):
- soft-deleted transaction is absent from list, dashboard totals, Planify spent, export, assistant tools;
- restore brings it back; restoring a transaction restores its cascaded holds but not separately-deleted ones;
- cross-user restore / hard delete → 404;
- hard delete of a live item → 409; empty Trash counts correctly;
- `live.test.ts` guard.

Android: `I18nResourcesTest` (keys), unit test for the Undo state (row removed → restored → re-inserted in order).

## Out of scope

Auto-purge, Trash for categories/budgets, bulk multi-select restore (add if Trash lists grow long).
