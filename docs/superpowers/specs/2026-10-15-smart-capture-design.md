# Smart capture — design

Replaces "Auto-capture". Bank SMS, bank emails and UPI-app alerts become transactions, every
bank message is kept as a synced copy (Captured messages), and the same payment arriving by SMS
**and** email is stored once.

## Decisions (agreed)

- Free and Play-safe only. Sources are **notifications** (SMS app, Gmail/Outlook, UPI apps) read by
  the existing `TransactionNotificationListenerService`. No Gmail API (paid CASA review), no email
  forwarding (needs a domain). Reading the SMS inbox (`READ_SMS`) is phase 4, behind a build flag,
  only after Play approves the "SMS-based money management" declaration.
- The **phone parses** (Kotlin, works offline); the **server decides** add / duplicate / review /
  ignore and stores the copy, in one endpoint, under a per-user lock.
- Message copies live **on the server, synced**; offline they wait in the Room outbox and show as
  "Waiting to sync".
- Defaults: payments are added automatically; a *possible* duplicate asks the user.

## Detection (Android `notifications/CaptureParser.kt`)

Input: source (sms / email / upi), sender (notification title), text (title + big text).

- Bank-ness: SMS sender is a DLT header (`XX-ABCDEF`, optional `-S/-T/-G/-P` suffix) or a known
  short name; email/UPI text needs an account/card/UPI marker. Otherwise → not captured, not stored.
- Ignored (stored with a reason, never a transaction): OTP / verification codes (**not stored at all**),
  promotions/offers/loans, failed/declined, future debits (mandate, autopay, "will be debited"),
  money requests, balance-only messages.
- Extracted: amount, direction, payee (name or UPI id), reference number, account/card last 4,
  available balance, the transaction date written in the message.
- Stored text is masked: digit runs of 9+ keep only their last 4.
- A corpus test (`CaptureParserTest`) covers HDFC, SBI, ICICI, Axis, Kotak, GPay, PhonePe, Paytm and
  every ignore reason.

## Duplicates (server `capture/decide.ts`, pure)

Per message, under `pg_advisory_xact_lock(user)`:

1. Same message id already stored → return it (idempotent retry).
2. Reference number equal to a stored message's (last 7 days) → **duplicate**. Two different
   reference numbers are never duplicates.
3. Else candidates = live transactions with the same amount and type within 30 min (3 h if either
   side is an email), with matching last 4 when both have it.
   - one candidate that was itself captured, with matching last 4 or payee → **duplicate**;
   - any other candidate (several, or a manual entry) → **review** if the user's duplicate setting is
     "Ask me", else add;
4. Else → **added** (or **review** when the capture mode is "Ask me first").

A duplicate fills in the existing transaction's merchant when it is "Unknown".

## Data

Both schema files. The user runs `npm run db:push`.

- `captured_messages`: `id uuid pk` (made on the phone), `user_id`, `source`, `sender`, `package`,
  `body` (masked), `received_at`, `amount`, `type`, `payee`, `ref_no`, `last4`, `balance`,
  `status` (added / duplicate / review / ignored), `reason`, `transaction_id` (fk, set null),
  `duplicate_of` (message id), `created_at`. Indexes `(user_id, received_at)`, `(user_id, ref_no)`.
- `user_settings`: `capture_sources` (csv of sms,email,upi), `capture_mode` (auto/ask),
  `capture_duplicates` (ask/keep), `capture_keep_messages` (bool), `capture_retention_days`
  (0 = forever), `capture_blocked_senders` (csv). `auto_capture_enabled` stays the master switch;
  `auto_capture_packages` is no longer read.
- Retention: older copies are deleted on each insert. Account deletion cascades.

## API (auth required, user-scoped)

- `POST /api/capture/messages` — ingest one message (≤ 4 KB body) → `{ message, transaction? }`.
- `GET /api/capture/messages?status=&transactionId=&page=` — history.
- `PATCH /api/capture/messages/[id]` `{ action: add | same | different | ignore }`.
- `DELETE /api/capture/messages/[id]`, `DELETE /api/capture/messages` (all).

## Android

- Listener → parser → `FinanceRepository.capture()` → endpoint, or the outbox (`kind = capture`)
  when offline. Queued captures show in lists like queued transactions.
- Notifications: "Added ₹X to Y" (existing description reply), "Review: looks like the same payment"
  (Same / Different), "Ask me first" (Add / Ignore).
- Settings → **Smart capture** page: master switch + notification-access row, sources, mode,
  duplicates, blocked senders, keep copies / keep for, delete all, open Captured messages.
- **Captured messages** screen: by day, source icon, sender, time, masked amount, status chip,
  filter chips; detail sheet with full text, found fields, linked transaction and the actions.
- Strings in all five languages; search index rows; help entry.

## Out of scope (later)

Assistant access to messages, export of messages, email forwarding, SMS inbox catch-up (phase 4).
