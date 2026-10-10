# Bills on Cloudinary — design

Module 2 of 4 in the bills initiative (1 Trash → **2 Bills** → 3 Bill AI → 4 Assistant + email).
Builds on Module 1 (`docs/superpowers/specs/2026-10-10-trash-soft-delete-design.md`): bills follow the
same `deleted_at` / Trash rules.

## Goal

Attach photos or PDFs of a bill to any expense or income (manual or auto-captured); view, replace and delete
them; deleted bills go to Trash, and deleting forever also removes the file from Cloudinary. Uploads must keep
working with thousands of users at once, survive bad networks and Cloudinary outages, and resist abuse.

## Decisions (agreed)

| Topic | Decision |
|---|---|
| Pages | Up to **5** per transaction (`position` 0–4) |
| Capture | Google ML Kit **Document Scanner** (on-device, auto-crop, multi-page, no camera permission) + system **photo picker** |
| Formats | jpg, png, webp, heic, **pdf**; max **10 MB** per file |
| Privacy | Cloudinary `authenticated` delivery type; **signed, non-expiring, unguessable** URLs handed out only to the owner |
| Offline | **Queue on the phone and retry** (WorkManager); nothing lost on app close |
| Entry points | Add/edit expense · transaction details · "Add bill" on the capture notification · paperclip on list rows |
| Quota | **500 MB stored + 50 uploads/day** per user, configurable via env |
| Upload path | Phone → Cloudinary **directly** (signed); our API only reserves, signs, confirms |
| Masking | Bill images are **blurred** while amount masking is locked; tap → PIN |

Out of scope: reading/explaining bills (Module 3), assistant + email (Module 4).

## Architecture

```
Phone ──(1) reserve──▶ API ──▶ Postgres (bills: pending)
Phone ◀─ signed params ─┘
Phone ──(2) multipart file──▶ Cloudinary (authenticated, overwrite=false)
Phone ──(3a) confirm {version, signature}──▶ API ─┐
Cloudinary ──(3b) webhook (X-Cld-Signature)──▶ API ─┴─▶ bills: ready   (first wins, second no-op)
Vercel cron (daily, CRON_SECRET) ──▶ API: expire pending · retry purges · reconcile bytes
```

Server SDK: the official `cloudinary` npm package (v2 API: `utils.api_sign_request`, `utils.url` with
`sign_url`, `utils.verifyNotificationSignature`, `uploader.destroy`, `api.delete_resources_by_prefix`,
`api.resources`). Signing code is security-critical, so it is not hand-rolled.

## Data

Both schema files (`src/db/schema/bills.schema.ts` exported from `schema/index.ts`, **and** `src/db/schema.ts`).
The user applies the schema change; the implementation never runs `db:push`.

`bills`

| Column | Type | Notes |
|---|---|---|
| `id` | uuid pk | |
| `user_id` | text → user.id, cascade | |
| `transaction_id` | uuid → transactions.id, cascade | |
| `public_id` | text, unique | `spendsync/bills/<userId>/<uuid v4>`; chosen by the server only |
| `position` | smallint | 0–4 |
| `status` | text enum `pending` / `ready` | |
| `format` | text null | from Cloudinary (jpg, png, pdf…) |
| `bytes` | integer null | from Cloudinary; reconciled by cron |
| `width`, `height` | integer null | |
| `pages` | integer null | PDFs |
| `version` | integer null | Cloudinary version (needed in URLs) |
| `client_key` | uuid | idempotency key from the phone; unique `(user_id, client_key)` |
| `created_at`, `updated_at` | timestamp | |
| `deleted_at` | timestamp null | Trash |

Indexes: `(transaction_id)`, `(user_id, deleted_at)`, unique `(user_id, client_key)`.

`bill_purges` (outbox — must outlive the cascades)

| Column | Type |
|---|---|
| `id` | uuid pk |
| `public_id` | text (or a folder prefix when `kind = 'prefix'`) |
| `kind` | text enum `asset` / `prefix` |
| `attempts` | integer default 0 |
| `next_at` | timestamp default now |
| `last_error` | text null |
| `created_at` | timestamp |

`bill_upload_counts` (daily cap that deleting can't reset)

| Column | Type |
|---|---|
| `user_id` | text → user.id, cascade |
| `day` | date (UTC) |
| `count` | integer |

Primary key `(user_id, day)`. Rows older than 7 days are deleted by the cron.

`liveBill = isNull(bills.deletedAt)`; the Module 1 guard test is extended to `.from(bills)`.

## Backend

Code in `src/bills/` (`cloudinary.ts` client + pure helpers, `service.ts`, `purge.ts`), schemas in
`src/types/bill.types.ts`, routes under `src/app/api/`. All routes `withApi(..., { auth: 'required' })`
except the webhook and cron (own auth, below).

### Endpoints

| Method + path | Behaviour |
|---|---|
| `POST /api/transactions/:id/bills` | **Reserve.** Body `{ clientKey: uuid, contentType, bytes ≤ 10 MB, position? }`. Checks: transaction is the user's and live; < 5 non-deleted bills on it; stored bytes (ready + pending, incl. Trash) + `bytes` ≤ quota; `bill_upload_counts` for today (UTC) < daily cap, incremented atomically by the reserve (`INSERT … ON CONFLICT DO UPDATE SET count = count + 1 RETURNING count`). Same `clientKey` → returns the existing reservation (re-signed). Returns `{ billId, uploadUrl, params }` — `params` = `api_key, timestamp, public_id, type=authenticated, overwrite=false, allowed_formats=jpg,png,webp,heic,pdf, notification_url, signature`. 503 `BILLS_NOT_CONFIGURED` without Cloudinary env. |
| `POST /api/bills/:id/sign` | Re-sign a still-pending reservation (queued > 1 h). |
| `POST /api/bills/:id/confirm` | Body = Cloudinary's upload response subset `{ public_id, version, signature, format, bytes, width, height, pages? }`. Verifies `signature == api_sign_request({public_id, version})` and `public_id` equals the reserved one. Sets `ready`. If `bytes` > 10 MB → destroy + 422. Idempotent. If this bill **replaces** a position, the previous live bill at that position is soft-deleted in the same DB transaction. |
| `POST /api/bills/webhook` | Cloudinary `notification_url`. Verifies `X-Cld-Signature` = sha1(raw body + `X-Cld-Timestamp` + secret) and timestamp < 2 h old (SDK `verifyNotificationSignature`). Looks up by `public_id`; same confirm logic. Unknown `public_id` → destroy it (not ours). Always 200 after verification. |
| `GET /api/transactions/:id/bills` | Live, ready bills ordered by `position`, each with signed URLs: `thumb` (`c_fill,w_300,h_300,f_auto,q_auto`, `pg_1` for PDF), `full` (`c_limit,w_2000,f_auto,q_auto`), `pages[]` for PDFs (`pg_n`), `blurred` thumb (`e_blur:2000`) for pre-API-31 masking, `original` for PDFs. Pure computation, no Cloudinary call. |
| `DELETE /api/bills/:id` | Soft delete → Trash. Idempotent. |
| `POST /api/bills/:id/restore` | Clear `deleted_at`. Back at its old `position` if free, else the first free one. 409 `PARENT_DELETED` if its transaction is in Trash; 409 `BILL_LIMIT` if the transaction already has 5 live bills. |
| `GET /api/bills/usage` | `{ bytesUsed, bytesLimit, uploadsToday, uploadsPerDay }` for Settings → Data. |
| `GET /api/cron/bills` | `Authorization: Bearer ${CRON_SECRET}`. (1) pending > 24 h → destroy asset (ignore not-found) + delete row; (2) due purges → destroy / delete-by-prefix, backoff `2^attempts` min capped 24 h, alert log after 10; (3) reconcile `bytes` from `api.resources` by prefix (500/page) and destroy files > 10 MB. Scheduled in `backend/vercel.json` `crons` (daily). |

### Changes to existing code

- `transactions` list (`transactionService.getAll`, assistant search tool) returns `billCount` (correlated
  `COUNT(*)` of live, ready bills; index on `transaction_id`).
- Module 1 soft delete / restore of a transaction cascades to its bills with the same `deleted_at`
  (same rule as holds).
- Trash list (`src/trash/service.ts`): kind `bill` — bills nested under a deleted transaction, plus bills
  deleted on their own whose transaction is live. `deleteForever('bill')`, `empty()` and hard delete of a
  transaction **insert `bill_purges` rows in the same DB transaction**, then attempt `destroy` inline
  (`invalidate: true`); success removes the purge row.
- `DELETE /api/account`: inserts a `prefix` purge for `spendsync/bills/<userId>/` before the user delete,
  then attempts it inline; account deletion never waits on Cloudinary.

### Env (`backend/.env.example`, validated in `src/config/env.ts`, all optional)

```
# Bills (Cloudinary). Without these three, bill endpoints answer 503 BILLS_NOT_CONFIGURED.
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
# Protects /api/cron/bills (Vercel sends it automatically when set in the project).
CRON_SECRET=
BILLS_MAX_BYTES_PER_USER=524288000
BILLS_MAX_UPLOADS_PER_DAY=50
```

`notification_url` = `${API_URL}/api/bills/webhook` (existing `API_URL`).

### Security

1. Forged confirm → response signature checked against the server-reserved `public_id`; the client never picks a `public_id`.
2. IDOR → every statement scoped by `user_id`; random UUID in paths; URLs only returned to the owner.
3. Swapping content later → `overwrite=false` is part of the signed params.
4. Signature reuse → bound to one `public_id`, 1 h `timestamp`; worst case re-uploads into an already-counted slot.
5. Quota abuse → DB-enforced (works across serverless instances); bytes reconciled from Cloudinary daily; > 10 MB destroyed.
6. Malicious files → signed `allowed_formats`; viewing only through Cloudinary re-encoded derivatives; PDF originals opened only by the system viewer.
7. Metadata/GPS → phone re-encodes photos (≤ 2400 px, JPEG q85) which drops EXIF; derivatives strip metadata.
8. Webhook/cron spoofing → `X-Cld-Signature` + 2 h window; `CRON_SECRET` bearer.
9. Stolen session → same exposure as other user data; hard delete still needs Trash first.

### Scale notes

- Image bytes never pass through the API; reserve/confirm are 2–3 small indexed queries.
- Signing and URL generation are local (no Cloudinary API calls); Admin API is used only by the daily cron.
- Deploy note: use the database provider's **pooled** connection string (`DATABASE_URL`) — many serverless
  instances each open connections.

## Android

### Data (`data/bills/`)

- `BillsApi` endpoints added to `AppApiService`; DTOs `BillDto` (id, position, status, format, pages, thumb,
  full, blurred, pageUrls, original), `BillReservation`, `BillUsage`.
- `BillRepository`: `list(txId)`, `delete/restore`, `usage()`.
- `BillUploader`: compress (photos → JPEG q85, longest side 2400 px, EXIF dropped by re-encoding; PDFs
  untouched), upload multipart to Cloudinary with a progress-reporting `RequestBody`, then confirm.
- `BillUploadQueue`: persisted list in `SessionDataStore` of `{ clientKey, localUri (copy in app files dir),
  txId or localTxKey, position, billId?, state }`; `BillUploadWorker` (WorkManager, network constraint,
  exponential backoff) drains it; re-signs when the signature is > 50 min old; deletes the local copy on
  confirm. New expenses: items carry `localTxKey`, rebound to the real id after the create call returns.
  Cleared on sign-out.
- `TransactionDto.billCount`.

### UI (`ui/bills/`)

- `BillStrip` — 72 dp rounded (12 dp) thumbnails + Add tile (< 5); long-press `AppTooltip` "Page n of m".
- `BillSourceSheet` (`AppSheet`) — **Scan bill** (ML Kit scanner, JPEG pages + PDF) / **Choose from gallery**
  (photo picker: images + PDF).
- `BillViewer` — full screen pager; pinch / double-tap zoom with Compose transform gestures; top bar
  "Page n of m", `AppIconButton`s Replace, Delete (→ Trash + Undo bar), Open PDF (PDFs).
- Masking: when masking is on and locked, thumbnails/viewer render `Modifier.blur` (API 31+) or the
  server's `blurred` URL (older), with a "Tap to unlock" overlay that calls `requestUnlock()`.

Upload states (every animation through `motionSpec(...)`; off with Settings → Animations / system setting):

| State | Visual |
|---|---|
| Preparing | local thumbnail + shimmer (`MotionKind.Skeleton`) |
| Uploading | thumbnail at 60% alpha + determinate ring = real progress (`Transitions`) |
| Confirming | indeterminate ring |
| Done | ring → check, fades in 600 ms; thumbnail to 100% (`Entrance`) |
| Waiting | cloud-off badge, "Waiting to upload" |
| Failed | error badge; tap → Retry / Remove |
| Deleted | tile shrinks + fades, neighbours slide; Undo bar |

Typography (existing `Typography`, no new fonts): section title `titleMedium` 16 sp; captions `labelSmall`
11 sp; storage line `bodySmall` 12 sp; errors/quota `bodyMedium` 14 sp in `colorScheme.error`. Colours from
theme tokens only (`primary` ring, `error`, `surfaceVariant` Add tile).

### Entry points

1. `AddExpenseScreen` — "Bill" row under the note: Tonal `AppButton` "Attach bill" (paperclip) → `BillSourceSheet`;
   strip of pending pages; uploads start after Add (new) or immediately (edit).
2. Transaction details (`TransactionInfoDialog`) — "Bills" section with `BillStrip` + viewer.
3. `TransactionCaptureNotifier` — "Add bill" action → `BillLinks` deep link → app opens the scanner for that id.
4. Home / Transactions rows — paperclip icon (+ count when > 1) when `billCount > 0`.
5. Settings → Trash — `kind = "bill"` rows (48 dp thumbnail, blurred if masked, "Bill · <merchant>").
6. Settings → Data — "Bills: X MB of Y MB used"; `AppSearchIndex` rows for both.

All strings in en/hi/es/fr/de; content descriptions per tile ("Bill page 1, uploading 40%").

## Failure handling

| Failure | Behaviour |
|---|---|
| Offline / Cloudinary down | Item stays queued with a local copy; worker retries with backoff; tile "Waiting to upload". |
| Signature expired | Worker calls `/sign`, retries. |
| App killed after upload | Webhook confirms; else next queue run confirms. |
| Webhook and phone both lost | Cron removes the pending row + asset after 24 h. |
| Cloudinary destroy fails | Purge row retried by cron with backoff; never orphaned. |
| API/DB down | Uploads stay queued; Coil disk cache shows already-seen bills. |
| Quota hit | Reserve refused before any upload; message "Storage full — empty Trash or delete old bills". |

## Tests

Backend (vitest, pure where possible):
- upload signature matches Cloudinary's documented example; response-signature verify (accepts real, rejects
  tampered `public_id`/`version`); webhook signature verify + 2 h window;
- quota decisions (bytes, daily, 5-page cap) as pure functions; restore position choice (old slot, first free, `BILL_LIMIT`);
- confirm state machine (pending→ready, replay no-op, replace soft-deletes previous) as a pure reducer;
- purge backoff schedule; signed-URL builder produces `authenticated` + `s--…--` URLs with the expected
  transformations;
- Trash guard extended to `bills`.

Android (JVM unit): compression drops EXIF and bounds size; queue state transitions incl. `localTxKey` rebind
and re-sign threshold; `BillLinks` parse; `I18nResourcesTest`.

Manual checklist after the user's `db:push` + env + deploy: upload photo / PDF / scan; airplane-mode queue;
kill app mid-upload; replace; delete → Undo → Trash → restore; delete forever → asset gone in Cloudinary
console; masking blur; quota message (temporarily lower `BILLS_MAX_UPLOADS_PER_DAY`).
