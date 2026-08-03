# Notification-Based Auto-Capture of Transactions

Date: 2026-08-03
Status: Approved

## Problem

Users currently have to manually enter every transaction. Most expense/income
events already surface as a device notification (UPI app payment
confirmation, bank SMS forwarded through the default messaging app). Reading
those notifications and auto-creating transactions removes that manual step.

## Scope (MVP)

In scope:
- Opt-in, off by default.
- Listens only to an allowlisted set of apps (UPI/banking apps + the default
  SMS app) — not every notification on the device.
- Auto-creates a transaction when amount + direction (debit/credit) can be
  confidently parsed from the notification text.
- Every auto-captured transaction gets a follow-up notification asking for a
  description (notifications never carry a user-authored note).

Explicitly out of scope for v1 (revisit only if real-world miss rate is bad):
- Per-bank/per-app parser templates.
- ML-based categorization.
- SMS permission path (`READ_SMS`/`RECEIVE_SMS`) — Notification Listener
  reading the default SMS app's own notifications covers bank SMS without
  that heavily-restricted permission.

## Architecture

### Components

- `TransactionNotificationListenerService : NotificationListenerService`
  Filters incoming notifications by allowlisted package name, extracts
  title/text from `Notification.extras`, passes raw text to the parser.

- `NotificationTransactionParser`
  Pure function `(text: String) -> ParsedTransaction?`. Plain Kotlin regex,
  no new dependency. Returns `null` when amount or direction can't be
  confidently extracted — no partial/garbage transaction is created in that
  case.

  `ParsedTransaction(amount: Double, direction: Direction, payee: String?, refNumber: String?)`

  Extraction rules (seeded from a real sample: *"Dear UPI user A/C X0790
  debited by 30.00 on date 03Aug26 trf to MANISH DINESHPRA Refno
  838274000171..."*):
  - Amount: currency marker (`₹`, `Rs.`, `INR`) or a decimal number adjacent
    to a direction keyword.
  - Direction: `debited`/`paid`/`spent` → debit, `credited`/`received` →
    credit.
  - Payee (optional): text following `trf to`, `to`, or `from`.
  - Ref number (optional): text following `Refno`, `UPI Ref`, `Txn ID`.

- Service instantiates its own `SessionDataStore(applicationContext)` and
  `FinanceRepository` — same construction pattern `MainActivity` already
  uses. No app-wide DI container exists in this codebase; not adding one for
  this feature.

- `RecentCaptures` — in-memory (service-lifetime) ring buffer of
  `(amount, direction, timestamp)` for dedup.

- `NotificationReplyReceiver : BroadcastReceiver` — catches the inline-reply
  `RemoteInput` text from the follow-up notification and calls
  `financeRepository.updateTransaction(id, note = reply)`.

- `PendingCaptureQueue` — small local queue (reuses `SessionDataStore`'s
  DataStore, not a new Room DB) for parsed transactions whose create-API
  call failed (e.g. offline). Retried via `WorkManager` on connectivity
  regain.

### Data flow

1. Notification posted by an allowlisted app →
   `TransactionNotificationListenerService.onNotificationPosted`.
2. Package name checked against user-enabled allowlist (Settings). Not
   enabled → ignored immediately, no parsing.
3. Text extracted, passed to `NotificationTransactionParser`.
4. Parse fails (no amount/direction) → dropped, nothing happens.
5. Parse succeeds → checked against `RecentCaptures` dedup buffer (same
   amount + direction within ~5 minutes → dropped as duplicate).
6. Not a duplicate → `financeRepository.createTransaction(amount, direction,
   merchant = payee ?: "Unknown", category = "Other", sourceApp =
   packageName)`.
   - Success → record added to `RecentCaptures`; follow-up "add a
     description?" notification posted with an inline-reply action.
   - Failure (e.g. offline) → parsed transaction pushed to
     `PendingCaptureQueue`; `WorkManager` retries on connectivity regain,
     then follows the same success path.
7. User replies inline to the follow-up notification →
   `NotificationReplyReceiver` → `updateTransaction(id, note = reply)`.

## Onboarding & permissions

- Settings gets a new "Auto-detect transactions" section: master toggle +
  per-app checkboxes drawn from a built-in allowlist (GPay, PhonePe, Paytm,
  common bank apps, default SMS app).
- `NotificationListenerService` access can't be requested via a runtime
  permission dialog. Toggling the master switch on shows an explainer
  screen first, then deep-links to
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` — this is a broad,
  sensitive system permission and users bail if surprised by the system
  prompt without context.

## Category default

Auto-captured transactions use `category = "Other"`, matching the existing
fallback convention (`AddExpenseScreen.kt:699-700`, `selectedCat ?: "Other"`).
`"Other"` is not one of the seeded default categories but is accepted as a
free-text category value like any user-entered one (backend treats
`category` as a plain string field).

## Error handling

- Unparseable notification → dropped silently, no transaction created.
- Create-API failure (offline, server error) → queued locally, retried via
  `WorkManager`, not dropped. (This is the one place scope was extended
  beyond the original ask — silently losing a transaction on a network blip
  would defeat the point of automation.)
- Duplicate signal (bank SMS + UPI app both notify the same payment) →
  deduped via `RecentCaptures`, second one dropped.

## Testing

- `NotificationTransactionParser` is a pure function → unit tests against a
  table of real sample notification strings (including the SBI/PhonePe
  sample above), asserting extracted amount/direction/payee.
- `RecentCaptures` dedup buffer → unit test with synthetic timestamps.
- Service/notification/WorkManager wiring is not practically unit-testable
  → manual verification on-device via `/run` (requires the user to grant
  Notification Access, which can't be automated).

## Open questions / follow-ups (not blocking)

- Real-world parser miss rate across banks not yet measured — if too many
  notifications get silently dropped, revisit per-app templates (explicitly
  deferred, not rejected).
