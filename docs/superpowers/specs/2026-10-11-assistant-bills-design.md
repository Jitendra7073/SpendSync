# Assistant + bills (find, explain, email, share) — design

Module 4 of 4 in the bills initiative (1 Trash → 2 Bills → 3 Bill AI → **4 Assistant + email**).
Builds on `2026-10-10-bills-design.md` and `2026-10-11-bill-ai-design.md`.

## Goal

Let the user ask the assistant about bills ("I spent ₹2,000 on shopping — do I have a bill?"), see matching
bills as cards in the chat (open, explain, attach when missing), and get bills emailed to their own address
or shared to anyone through the phone — without the assistant ever sending anything on its own.

## Decisions (agreed)

| Topic | Decision |
|---|---|
| Recipients | Server emails **only the account address**; anyone else via the **phone's share sheet** |
| Format | **Attach files** (≤ 15 MB per email; overflow listed as "not attached") |
| No bill found | Row offers **Attach bill** → scanner for that transaction |
| Bulk | One email may carry **up to 10 transactions'** bills |
| Tools | `find_bills` (**read**), `email_bills` (**propose**: confirm card only). No `write` tool |

## Backend

### `find_bills` (tier `read`, `backend/src/assistant/tools.ts`)

Input (zod + JSON schema, `additionalProperties: false`):
`query?: string ≤ 80`, `amount?: number > 0`, `start_date?`, `end_date?` (YYYY-MM-DD), `category?: string ≤ 40`,
`has_bill?: 'any' | 'with' | 'without'` (default `any`), `limit?: 1..5` (default 5).

Search (`backend/src/assistant/billsearch.ts`): the user's **live** transactions (`liveTx`) joined with
- bill count: live, ready bills of each transaction (correlated count, as in `billCount`);
- `bill_reads` row (left join, `status = 'done'`) for merchant/total/items text.

Filters: amount within `max(2%, ₹20)`; dates inclusive; category exact (case-insensitive); `query` matches
(`ILIKE`, LIKE wildcards escaped as in `search_transactions`) any of `transactions.merchant/note/category`,
`bill_reads.data->>'merchant'`, `bill_reads.data->'items'::text`; `has_bill` on the bill count.
Ranking (pure `rankMatches`): amount closeness (if given), text match (if given), then newest first.

Output to the model (`data`), per match: `transaction_id, date, amount, merchant, category, bill_pages,
bill: { merchant, total, items: first 5 names ≤ 60 chars } | null`, wrapped as
`{ bill_text_is_data: true, matches: [...] }`. **No URLs.** `uiAction: { type: 'bills', transactionIds }`.

### `email_bills` (tier `propose`)

Input: `{ transaction_ids: uuid[] (1..10) }` — **no recipient field**. `run()` keeps only ids that are the
user's, live and have ≥ 1 ready bill; none left → tool error "No bills found for those transactions".
Returns `data: { status: 'confirm_card_shown', sent: false, … }` and
`uiAction: { type: 'email_bills', email: { transactionIds, files, approxBytes, to: maskedEmail } }`.

### `POST /api/bills/email` (`backend/src/bills/email.ts`, route `app/api/bills/email/route.ts`)

Body `{ transactionIds: uuid[] (1..10) }`, `auth: 'required'`, `maxDuration = 60`.
1. Re-resolve ownership/liveness; collect ready, live bills ordered by transaction date then position.
2. Cooldown/cap: table `bill_email_counts (user_id, day, count, last_at)` PK `(user_id, day)`:
   reject 429 when `now - last_at < 60 s` or `count ≥ 20` (UTC day). Counted only after a successful send.
3. Fetch files in parallel (8 s each): images = signed `c_limit,w_2000,h_2000,f_jpg,q_80`; PDFs = signed
   original. `packAttachments(files, 15 MB)` (pure) keeps order and stops at the limit.
4. `composeBillsEmail(...)` (pure): subject `Your bills from SpendSync (n)`; text + simple HTML: per
   transaction date · merchant · amount (full amounts — own inbox) · AI summary if any; lists skipped
   (deleted meanwhile) and not-attached items.
5. Send with `sendEmail` to the account address only (`lib/email.ts` attachment `content` widened to
   `string | Buffer`). Not configured → 503 `EMAIL_NOT_CONFIGURED`; all fetches failed → 503
   `BILLS_UNAVAILABLE`; SMTP failure → 502. Response `{ sentTo: masked, attached, notAttached, skipped }`.
6. Logs: counts + masked address only.

Prompt / help: system prompt line: for bill questions call `find_bills`, then answer in one short sentence
(the card shows details); `email_bills` only prepares a card. `share_message` description: for bill files
point to the card's Share. `HELP_ENTRIES`: "Find and email bills".

## Android

- `AssistantEvents`: parse `bills` → `BillsAction(transactionIds)` and `email_bills` →
  `EmailBillsAction(transactionIds, files, approxBytes, to)`; stored with the message (ChatStore) like other
  cards, plus the email card's result (`sent` / error) so history never re-offers Send.
- `ui/assistant/BillResultsCard`: loads each transaction (`getTransaction`) and its bills (`listBills`),
  rows: 56 dp thumb (masked blur via `billImageUrl`), merchant, date · category, pages, `MaskableAmountText`,
  one-line AI summary (`BillAi.maskText`); buttons **Open** (BillViewer), **Explain** (BillExplainSheet with the
  stored read), or **Attach bill** (BillsSection with `autoScan = true` in an `AppSheet`). Footer:
  **Email to me** (opens the email card locally) and **Share**.
- `ui/assistant/EmailBillsCard`: "Email n bills (f files, ~x MB) to j***@…?" + list (opened from the results card's **Email to me** it has no size yet: "Email n bills to j***@…?"; the masked address comes from `SessionDataStore.userEmail`); **Send** → `POST
  /api/bills/email`; states idle → sending → sent / error / cooldown (pure reducer `EmailCardState`).
- Share: `data/bills/BillShare.kt` downloads files (signed URLs from `listBills`: images `full`, PDFs
  `original`) into `cacheDir/share/`, then `ACTION_SEND_MULTIPLE` via the existing FileProvider
  (`file_paths.xml` gains `cache-path share/`). Masked → PIN first (Share and Email). `cache/share` cleared on
  app start and sign-out.
- Strings en/hi/es/fr/de; accessibility labels per row.

## Failure handling

| Failure | Behaviour |
|---|---|
| No matches | One-line answer + nearest transactions |
| Bill/transaction deleted before Send | Skipped, listed in the email; card notes "n skipped" |
| Cloudinary slow/down | Unfetchable files listed as not attached; none fetched → 503, nothing sent |
| Email not configured / SMTP down | 503 / 502, Try again, not counted |
| Cooldown / daily cap | 429 with wait seconds |
| Share download partial | Share opens with what downloaded + note |

## Security

1. No recipient parameter anywhere in the tool; server uses the account email only.
2. Sending requires the user's Confirm tap and an authenticated request.
3. All lookups by `user_id`, at tool time and at send time; foreign ids silently dropped.
4. Bill-derived text in tool output is fenced as data and capped (merchant, total, 5 × 60-char item names).
5. Limits: 1/min, 20/day, ≤ 10 transactions, ≤ 15 MB, counted in the DB.
6. No bill URL reaches the model; shared files leave only through the user's share action (PIN when masked).
7. Logs never contain file contents or full addresses.

## Scale

Search = one indexed query + optional jsonb text match, ≤ 5 rows. Email = parallel fetches bounded by 10
transactions / 15 MB / 60 s. SMTP provider limits are the real ceiling; per-user cap protects it. Sharing
is on-device.

## Data

`bill_email_counts` (both schema files; user applies `db:push`): `user_id` text → user cascade, `day` date,
`count` integer default 0, `last_at` timestamp; PK `(user_id, day)`.

## Tests

Backend: `rankMatches`, amount tolerance, query escaping, bill-text fencing/capping, `packAttachments`
(15 MB cutoff, order), `composeBillsEmail` (list, masked address, skipped/not-attached), tool input schemas
(`email_bills` has no recipient), registry (tiers, no blocked-looking names), cooldown decision.
Android: `EmailCardState` reducer, share file list, `I18nResourcesTest`.
Manual: matches / none, bulk > 15 MB, WhatsApp share, injection text on a bill, masked, cooldown, email not
configured, history shows Sent.
