# Home & Hold Tracker UX Updates

Date: 2026-08-05
Status: Approved

## Problem

Four follow-up UX issues surfaced after using the money hold tracker and
Home screen day-to-day:

1. Home's balance card headlines "Total Balance" (net + hold money), but
   the insufficient-balance spend guard only checks net balance — the big
   number on screen isn't the number that governs what you can spend.
2. The Holds screen is a flat list of individual holds. With more than a
   couple of holds per person, there's no way to see "how much do I owe/am
   I owed by this specific person" at a glance, and holds can't be edited
   once created (only settled).
3. Home's income/expense tiles silently reduce an already-fetched month of
   data down to a single day's transactions, which reads as a bug the
   first time you notice it. Separately, the date picker only ever
   navigates day-by-day — jumping to a different year takes many taps.
4. The Holds screen (built in the previous feature) has some rough
   alignment/dead-code edges flagged in its final review but never
   cleaned up.

## Scope

In scope:
- Home balance card: promote Net Balance to the headline position, demote
  Total Balance to a sub-stat (swap with Hold Money's current position).
- Holds screen restructured as a person-grouped list; tapping a person
  opens a new Person Detail screen listing that person's individual
  holds, each with Edit / Mark as settled / Delete actions.
- Hold editing (person name, expected return date) via the existing
  `PATCH /api/holds/[id]` endpoint — already supports both fields, no
  backend change needed. Amount stays immutable (unchanged design
  decision from the original hold tracker spec — it's a snapshot, not a
  live value).
- Editing a hold's name or date cancels and reschedules its on-device
  reminder, so the reminder notification's saved data never goes stale.
- Home income/expense tiles sum the already-fetched month directly
  instead of re-filtering to the selected day.
- `MonthPickerDialog` gains Year → Month → Day drill-down navigation
  modes. Purely a navigation aid — whatever date you land on, Home still
  shows that month's totals (no new data aggregation, no backend change).
- General alignment cleanup of the Holds screen's existing rows (dead
  imports, a no-op `Spacer`, row padding consistency).

Explicitly out of scope:
- Changing the insufficient-balance spend guard to include hold money —
  already decided against in the hold tracker's final review; the guard
  correctly stays net-balance-only.
- A year-level data aggregate (whole-year income/expense totals) — no
  backend support exists for this today, and it's not needed since the
  calendar's year/month modes are navigation-only.
- Partial-hold deletion cascades, hold amount editing, or any other
  change to the hold tracker's original data model.

## Home balance card

Swap headline and sub-stat roles: `Net Balance` (raw `allTimeBalance`)
becomes the 28sp bold headline; `Total Balance` (`allTimeBalance +
holdMoney`) and `Hold Money` become the two 14sp sub-stats in the row
below, in the same positions those two currently occupy. `Hold Money`
keeps its tap target (opens Holds).

## Holds screen restructure

**Main Holds screen** — grouped by `personName`. Each row: person name,
net amount for that person (summed across their pending holds, signed by
direction — same arithmetic already used for Home's `holdMoney`
aggregate), and a hold count. Tapping a row navigates to the Person
Detail screen for that name.

**Person Detail screen** (new) — a list of that person's individual
holds (both pending and settled), each row showing amount / expected
return date / status, with per-row actions:
- **Edit** — opens a dialog to change `personName` and/or
  `expectedReturnDate` (reusing the drill-down date picker from item 3).
  Calls `FinanceRepository.updateHold(id, personName=, expectedReturnDate=)`.
- **Mark as settled** — existing action, unchanged, only shown when
  `status == "pending"`.
- **Delete** — calls the already-built (previously unused)
  `FinanceRepository.deleteHold(id)`.

**Reminder resync on edit**: after any successful edit that touches
`expectedReturnDate` (and, defensively, also on a `personName`-only edit,
since the scheduled `WorkManager` job's `workDataOf` snapshot bakes the
name into the reminder's notification text at schedule time) — cancel
the existing `HoldReminderWorker` job for that hold id and schedule a
fresh one with the updated data. Deleting a hold also cancels its
reminder, same as settling one already does.

## Home income/expense tiles — month-wise

`HomeScreen`'s `dailyTransactions` re-filter (`monthlyTransactions`
filtered down to `parsedDate.isEqual(dateFilterState.selectedDate)`) is
removed. `totalIncome`/`totalExpenses` sum `monthlyTransactions` directly
— no new network fetch, since the month's data is already being fetched
for the transaction list below the tiles.

## Calendar — Year → Month → Day drill-down

`MonthPickerDialog` gains an internal navigation mode (`enum class
PickerMode { YEAR, MONTH, DAY }`, starting at `DAY` to preserve today's
default behavior when opened from the calendar icon):
- **Year mode**: a scrollable list/grid of years centered on the current
  view year. Tapping a year drills into Month mode for that year.
- **Month mode**: a 12-month grid (Jan–Dec) for the selected year.
  Tapping a month drills into Day mode for that month.
- **Day mode**: today's existing day grid, unchanged. A header control
  (tapping the current month/year label) lets the user jump back out to
  Month or Year mode instead of only stepping one month at a time via the
  `<`/`>` arrows, which remain for fine adjustment within Day mode.

Confirming a day still calls `onConfirm(selected)` exactly as today.
Landing on a month without picking a specific day is not a distinct
outcome — the mode switcher is purely a faster way to reach a target day
in a different month/year; `maxDate`'s future-disabling behavior applies
consistently across all three modes.

## Testing

- Pure logic: the person-grouping/summing function (group holds by
  `personName`, sum signed amounts) is a good candidate for a plain unit
  test, same pattern as the existing hold-money aggregate.
- No dedicated test for `MonthPickerDialog`'s new modes or the Compose UI
  changes — consistent with how this app has tested (or not tested)
  Compose UI throughout; verified manually via `/run`.

## Open questions / follow-ups (not blocking)

- The person-detail screen doesn't yet support bulk actions (e.g. "settle
  all" for a person) — not requested, not added.
