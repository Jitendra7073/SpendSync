# Amount Masking / PIN Privacy Feature

Date: 2026-08-05
Status: Approved

## Problem

Large amounts are visible to anyone glancing at the screen (shoulder-surfing,
phone left on a table). Users want an opt-in way to hide amounts over ₹1000
behind a local PIN, with a temporary "session" unlock rather than re-entering
a PIN for every single value.

## Scope

In scope:
- Any displayed amount > ₹1000 renders as `★★★★★` with an inline eye icon
  instead of the real value. Amounts ≤ ₹1000 always display normally.
- Opt-in via a new Settings toggle, "Hide large amounts" (off by default).
  Turning it on for the first time prompts PIN creation (4-digit numeric,
  entered twice to confirm) right there.
- Changing the PIN (Settings) requires the current PIN first, then the new
  PIN twice.
- PIN is never sent to the backend. Stored locally only, as a salted
  SHA-256 hash (`java.security.MessageDigest`, stdlib — no new dependency),
  never the raw PIN.
- Tapping any eye icon anywhere opens one shared PIN prompt. A correct PIN
  unlocks every masked amount app-wide simultaneously, for a duration set
  in Settings (30s / 1min / 5min / 15min, default 1min). Expiry re-masks
  everything automatically with no interaction needed.
- Applies everywhere an amount is currently shown via `formatInr`: Home's
  balance card + income/expense tiles + transaction rows, Analytics,
  Budget, the Holds list, Hold Detail, and Add Transaction's
  insufficient-balance messages.
- The unlock session is in-memory only — it does not survive an app
  restart. Reopening the app always starts re-masked, even mid-session.
  This is the safer default and avoids needing to persist/secure a
  session token across process death.

Explicitly out of scope:
- Biometric unlock (fingerprint/face) as an alternative to the PIN —
  not requested, would be a natural fast-follow.
- Per-amount or per-screen masking granularity — masking is app-wide:
  on or off, and unlocked or not, uniformly.
- Any backend change — this is a 100% local, client-side feature.
- Recovering a forgotten PIN — out of scope for v1; if this becomes a
  real problem, a "reset via re-confirming sign-in" flow is the natural
  follow-up, not designed here.

## Data model (local only — `SessionDataStore`)

- `amountMaskingEnabled: Boolean` (default `false`)
- `amountVisibilityDurationSeconds: Int` (default `60`)
- `pinHash: String?` (salted SHA-256 hex, `null` until a PIN is set)
- `pinSalt: String?` (random salt generated at PIN-set time, `null` until
  a PIN is set)

Hashing: `sha256(salt + pin)`. Salt is a random value generated once when
the PIN is first created (or changed) and stored alongside the hash —
never derived from anything guessable, and regenerated on every PIN
change so an old hash+salt pair is never reused.

## Shared visibility session

A small in-memory controller (analogous to the existing `DateFilterState`
pattern — created once in `MainScreen.kt`, threaded down to every tab)
exposing:
- `isVisible: Boolean` — `true` only between a successful unlock and its
  computed expiry instant.
- `requestUnlock()` — opens the shared PIN prompt.
- On correct PIN entry, sets `unlockedAt = now()` and schedules a single
  `LaunchedEffect` delay to flip `isVisible` back to `false` at expiry —
  no polling, no background work, purely a foreground Compose timer (the
  session doesn't need to survive backgrounding since app restart already
  clears it).

## `MaskableAmountText` composable

One reusable composable wrapping the existing `Text(text = formatInr(...))`
pattern used at every amount-display call site today:
- Renders the real formatted amount when `amount <= 1000` or the shared
  session `isVisible == true`.
- Renders `★★★★★` plus a small trailing eye icon (tap → `requestUnlock()`)
  when masked.
- Accepts the same styling parameters (`color`, `fontSize`, `fontWeight`,
  an optional `prefix` for the `"+"`/`"-"` sign some call sites already
  prepend) each existing call site already passes to `Text`, so retrofitting
  is a mechanical one-call swap per site, not a rewrite of the surrounding
  layout.

## Settings UI

New "Privacy" section in Profile/Settings:
- Toggle: "Hide large amounts" — turning on triggers PIN-creation dialog
  if no PIN exists yet; turning off does not clear the stored PIN (so
  re-enabling later doesn't require creating a new one).
- When enabled, two more rows appear: "Change PIN" (current PIN → new PIN
  → confirm new PIN) and "Visibility duration" (the 4-option picker).

## Error handling

- Wrong PIN on unlock attempt or PIN change: inline error message on the
  dialog, no lockout/rate-limiting for v1 (not requested; add only if
  real misuse is observed — this is a shoulder-surfing deterrent, not a
  security boundary against a determined attacker with the device).
- Wrong "current PIN" on a change attempt: same inline error, does not
  touch the existing stored PIN.

## Testing

- Pure logic: the salted-hash generation/verification function and the
  masking-threshold decision function (`shouldMask(amount, isVisible):
  Boolean`) are both plain, testable functions — unit tests for both.
- No dedicated test for the Compose UI retrofit across the ~8 touched
  files, or the session-expiry timer — verified manually via `/run`,
  consistent with how this app has tested Compose UI throughout.
