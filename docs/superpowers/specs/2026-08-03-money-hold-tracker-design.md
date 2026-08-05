# Money Hold / IOU Tracker

Date: 2026-08-03
Status: Approved

## Problem

Users lend and borrow small amounts of money informally (paying for a
friend, splitting a bill, borrowing cash) and have no way to track who
owes them or who they owe, or to get reminded when the money was expected
back.

## Scope

In scope:
- A `Hold` record created optionally alongside any transaction (expense
  or income) via an inline toggle in the existing Add Transaction form.
- Both directions: an expense (debit) transaction can create an
  `owed_to_me` hold (you lent, they owe you); an income (credit)
  transaction can create an `owed_by_me` hold (you borrowed, you owe
  them).
- A dedicated Holds list screen, reachable from a summary card on Home,
  showing pending/settled holds with a "mark as settled" action.
- One-time, on-device local reminders scheduled for the expected return
  date.
- Full-stack: new backend table + REST endpoints, plus the Android
  client changes to use them.

Explicitly out of scope for v1:
- Partial repayment tracking — settling is binary (pending → settled).
- Reminders that repeat/escalate after the due date — a single one-time
  notification on the due date, nothing further.
- Server-driven push notifications — reminders are scheduled and fired
  entirely on-device (WorkManager), not sent by the backend. Revisit only
  if cross-device/reinstall persistence becomes a real complaint.
- A new bottom-navigation tab for Holds — reached via a summary card
  instead, to avoid crowding the existing Home/Analytics/Budget/Profile +
  FAB layout.

## Data model

New `holds` table, backend (Drizzle), following the exact skeleton every
other table in this schema already uses (`uuid` PK
`defaultRandom()`, `userId text` FK to `user.id` with
`onDelete: 'cascade'`, `decimal(12,2)` for money, plain `timestamp`
created/updated columns with `defaultNow()`):

```
holds
  id                 uuid PK, defaultRandom()
  userId             text, FK -> user.id, onDelete cascade
  transactionId      uuid, FK -> transactions.id, onDelete cascade, not null
  direction          text enum ('owed_to_me' | 'owed_by_me'), not null
  personName         text, not null
  amount             decimal(12,2), not null   -- snapshot at creation time
  expectedReturnDate timestamp, not null
  status             text enum ('pending' | 'settled'), default 'pending'
  settledAt          timestamp, nullable
  createdAt          timestamp, defaultNow()
  updatedAt          timestamp, defaultNow()
```

`amount` is a **snapshot**, not derived from the linked transaction —
editing the transaction's amount later does not retroactively change what
the hold expects back. This is a deliberate decoupling: the hold
represents what was actually agreed to be returned at the time of lending,
not a live mirror of the transaction row.

`direction` mapping: an expense (debit) transaction → `owed_to_me`
(you paid, they owe you). An income (credit) transaction → `owed_by_me`
(you received, you owe them).

**Implementation gotcha to carry into the plan**: this backend
hand-duplicates schema in two places — `src/db/schema/holds.schema.ts`
(imported at runtime) and a mirrored inline definition in the flat
`src/db/schema.ts` (read by `drizzle-kit generate` for migrations, per
`drizzle.config.ts`). Missing the second one means the migration silently
doesn't include the new table.

## Backend API

New routes under `backend/src/app/api/holds/`, mirroring the
`transactions`/`budgets` route pattern exactly: `withApi()` wrapper
(auth, rate limiting, CORS, error mapping already centralized there),
Zod-validated input, a `holdService` class, the same
`{ success, data, error, meta }` response envelope.

- `POST /api/holds` — create. Body: `{ transactionId, direction,
  personName, amount, expectedReturnDate }`.
- `GET /api/holds` — list for the current user. Query filters:
  `status`, `direction`.
- `PATCH /api/holds/[id]` — update. Used both to mark settled
  (`{ status: 'settled' }`, sets `settledAt`) and to edit
  `personName`/`expectedReturnDate`.
- `DELETE /api/holds/[id]` — remove tracking without touching the
  underlying transaction.

Deleting the underlying transaction (`DELETE /api/transactions/[id]`,
already exists) cascade-deletes any linked hold at the DB level — no
extra backend code needed beyond the FK constraint.

## Android — Add Transaction flow

`AddExpenseScreen` gains an inline toggle, shown for both income and
expense, directly in the form (not a follow-up dialog after save):
"Expect this back?" (expense) / "Need to pay this back?" (income).

Toggling it on expands two fields inline:
- **Person name** — pre-filled from whatever payee/note text is already
  in the form, editable.
- **Expected return date** — reuses the app's existing date-picker
  pattern (date only, no time-of-day input).

Save sequencing: the transaction saves first via the existing
`FinanceRepository.createTransaction()` call, unchanged. Only if the
toggle was on does a second call, `FinanceRepository.createHold(...)`,
fire using the newly-created transaction's id. If that second call fails,
the transaction still stands — the user sees a distinct "saved, but
couldn't track the hold" error toast rather than the save appearing to
fail outright. The transaction is always the primary, protected outcome.

## Android — Holds screen

A summary card on Home ("You're owed ₹X · You owe ₹Y") — computed from
pending holds — links to a full Holds list screen. The list is
filterable by direction, each row shows person / amount / due date /
status, with a "Mark as settled" action per row.

## Android — Reminders

Reuses the `androidx.work:work-runtime-ktx` dependency already added for
the notification auto-capture feature — no new dependency. Creating a
hold schedules a one-time `WorkManager` request with an `initialDelay`
computed to the expected return date at a fixed default time (9:00 AM
local) — approximate timing is acceptable for a reminder, so this avoids
needing the more sensitive exact-alarm permission `AlarmManager` would
require. The worker posts a local notification ("Reminder: <person> was
expected to return ₹<amount> today" / "...you were expected to pay back
₹<amount> today", worded per direction).

Marking a hold settled or deleting it cancels its pending `WorkManager`
request by a unique work name derived from the hold's id.

## Error handling

- Hold-creation failure after a successful transaction save → transaction
  stands; distinct error toast; never roll back or silently drop the
  transaction to protect the hold.
- Deleting the source transaction → hold cascade-deletes at the DB level.
- Deleting/settling a hold → cancels its scheduled reminder.

## Testing

- Backend: Zod schema validation + service-layer tests for the new
  `holds` endpoints, matching whatever test pattern the existing
  `transactions`/`budgets` services already use.
- Android: unit tests for the reminder-scheduling date/delay math (pure
  function, no Android framework dependency) and any new pure
  formatting/mapping helpers (e.g. direction → notification wording).
  UI/integration verified manually via `/run`, as with the prior feature.

## Open questions / follow-ups (not blocking)

- Reminder fires at a fixed 9:00 AM local default with no user-facing
  time picker — if that turns out to be the wrong time for real usage,
  add a time picker in a fast-follow rather than blocking v1 on it.
