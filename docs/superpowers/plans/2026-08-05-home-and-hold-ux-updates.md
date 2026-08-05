# Home & Hold Tracker UX Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swap Home's balance-card headline/sub-stat roles, make its income/expense tiles month-wise, add Year/Month/Day drill-down navigation to the shared date picker, and restructure the Holds screen into a person-grouped list with an editable per-person detail screen.

**Architecture:** All Android-only, no backend changes — the hold-editing backend endpoint (`PATCH /api/holds/[id]`) already supports both fields being changed. Four independent-but-related UI changes: two small edits to an existing file (`HomeScreen.kt`), one substantial rewrite of an existing shared component (`MonthPickerDialog.kt`, kept backward-compatible for its other two call sites), one new pure/testable grouping function, and one new screen (`HoldDetailScreen.kt`) wired into a restructured `HoldsScreen.kt`.

**Tech Stack:** Kotlin, Jetpack Compose (existing app conventions only — no new dependencies).

Spec: `docs/superpowers/specs/2026-08-05-home-and-hold-ux-updates-design.md`

## Global Constraints

- No backend changes in this plan — everything needed already exists (`PATCH /api/holds/[id]` accepts `personName`/`expectedReturnDate`; `DELETE /api/holds/[id]` exists and is currently unused; `FinanceRepository.updateHold`/`deleteHold` already exist).
- Hold `amount` stays immutable — never add amount editing anywhere in this plan (it's a deliberate snapshot per the original hold tracker design).
- `MonthPickerDialog`'s public signature (`current`, `onConfirm`, `onDismiss`, `maxDate`) must not change — it has two other call sites (`AddExpenseScreen.kt`'s transaction date field, `AddExpenseScreen.kt`'s hold-return-date field) that must keep working unmodified. New Year/Month modes are additive, reached only by tapping the header label; opening the dialog still starts in Day mode exactly as today.
- Editing a hold's name or expected-return-date must cancel and reschedule its `HoldReminderWorker` job (via `HoldReminderWorker.cancel`/`.schedule`, same functions the settle flow already uses) — never leave a stale reminder scheduled with outdated data.
- Currency is always ₹ — use `com.example.spendsync.utils.formatInr`.
- Follow existing code style: no KDoc on obvious code, comments only for non-obvious "why".

---

### Task 1: Home — balance card swap + month-wise income/expense tiles

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt`

**Interfaces:** No new interfaces — internal-only changes to an existing composable's rendering and two `remember` computations.

Before editing, grep this file for `dailyTransactions` to confirm it's used only in the two `totalIncome`/`totalExpenses` computations being replaced — if it's referenced anywhere else, stop and report rather than silently deleting something still in use.

- [ ] **Step 1: Swap the balance card's headline and sub-stat**

Find the balance card's `Column` content (starts with `Text(text = LocalizationUtils.getTranslation("total_balance", language), ...)` right after `// ── Balance card ...` comment, ends after the `Row` containing the "Net Balance"/"Hold Money →" sub-stats). Replace it with:

```kotlin
                    Text(
                        text     = "Net Balance",
                        color    = NeutralWhite.copy(alpha = 0.80f),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (isBalanceLoading) {
                        SkeletonLine(modifier = Modifier.width(160.dp), height = 32.dp)
                    } else {
                        Text(
                            text       = formatInr(allTimeBalance ?: 0.0),
                            color      = NeutralWhite,
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Yellow accent bar
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BrandYellow),
                    )
                    if (!isBalanceLoading) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = LocalizationUtils.getTranslation("total_balance", language),
                                    color = NeutralWhite.copy(alpha = 0.70f),
                                    fontSize = 11.sp,
                                )
                                Text(
                                    text = formatInr((allTimeBalance ?: 0.0) + holdMoney),
                                    color = NeutralWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(
                                modifier = Modifier.clickable { onOpenHolds() },
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text("Hold Money →", color = NeutralWhite.copy(alpha = 0.70f), fontSize = 11.sp)
                                Text(
                                    text = formatInr(holdMoney),
                                    color = NeutralWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
```

(Only the headline label/value and the first sub-stat's label/value moved — the "Hold Money" sub-stat and its click handler are unchanged, reproduced above only for context/placement.)

- [ ] **Step 2: Make the income/expense tiles month-wise**

Find and replace this block (the `dailyTransactions` filter and the two totals derived from it):

```kotlin
    // 1. Filter by EXACT Selected Date from Top Bar — powers the Income/Expense
    // summary tiles only; the type filter below does not affect this.
    val dailyTransactions = remember(monthlyTransactions, dateFilterState.selectedDate) {
        monthlyTransactions.filter {
            try {
                val parsedDate = java.time.ZonedDateTime.parse(it.createdAt).toLocalDate()
                parsedDate.isEqual(dateFilterState.selectedDate)
            } catch (e: Exception) {
                false
            }
        }
    }

    // Calculate Summary Stats from daily data
    val totalIncome = remember(dailyTransactions) {
        dailyTransactions.filter { it.type == "credit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalExpenses = remember(dailyTransactions) {
        dailyTransactions.filter { it.type == "debit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
```

with:

```kotlin
    // Summary tiles now cover the whole selected month — monthlyTransactions
    // is already fetched scoped to dateFilterState.selectedDate's month
    // (see loadTransactions above), so no further date filtering is needed.
    val totalIncome = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == "credit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalExpenses = remember(monthlyTransactions) {
        monthlyTransactions.filter { it.type == "debit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt
git commit -m "feat: promote Net Balance to headline, make income/expense tiles month-wise"
```

---

### Task 2: Calendar — Year/Month/Day drill-down navigation

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/shared/MonthPickerDialog.kt`

**Interfaces:** Public `MonthPickerDialog(current, onConfirm, onDismiss, maxDate)` signature unchanged — this task only changes internals. Produces three new private composables (`DayGrid`, `MonthGrid`, `YearGrid`) local to this file, not consumed elsewhere.

This is a full-file rewrite — the header row's logic changes for all three modes and the body now conditionally renders one of three grids instead of always the day grid. Replace the entire file with:

```kotlin
package com.example.spendsync.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralWhite
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class PickerMode { YEAR, MONTH, DAY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerDialog(
    current: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    // When set, days/months/years after this are shown but disabled — used by
    // the add-transaction date field so a transaction can never be backdated
    // into the future.
    maxDate: LocalDate? = null,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    var viewYear  by rememberSaveable { mutableIntStateOf(current.year) }
    var viewMonth by rememberSaveable { mutableIntStateOf(current.monthValue) }
    var selected  by remember { mutableStateOf(current) }
    // Always opens in Day mode, exactly like before — Year/Month are reached
    // by tapping the header label to drill up, and exited by tapping a cell
    // to drill back down to Day mode.
    var mode by rememberSaveable { mutableStateOf(PickerMode.DAY) }
    var yearWindowStart by rememberSaveable { mutableIntStateOf(current.year - 5) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NeutralWhite)
                .padding(20.dp),
        ) {
            Text(
                text       = "Pick a date",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = NeutralBlack,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val atMaxMonth = maxDate != null &&
                    YearMonth.of(viewYear, viewMonth) >= YearMonth.from(maxDate)
                val atMaxYear = maxDate != null && viewYear >= maxDate.year
                val atMaxYearWindow = maxDate != null && yearWindowStart + 11 >= maxDate.year

                IconButton(onClick = {
                    when (mode) {
                        PickerMode.DAY -> if (viewMonth == 1) { viewMonth = 12; viewYear-- } else viewMonth--
                        PickerMode.MONTH -> viewYear--
                        PickerMode.YEAR -> yearWindowStart -= 12
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        tint               = BrandBlue,
                    )
                }

                val headerLabel = when (mode) {
                    PickerMode.DAY -> java.time.Month.of(viewMonth)
                        .getDisplayName(TextStyle.FULL, Locale.getDefault())
                        .replaceFirstChar { it.uppercase() } + " $viewYear"
                    PickerMode.MONTH -> "$viewYear"
                    PickerMode.YEAR -> "$yearWindowStart–${yearWindowStart + 11}"
                }
                Text(
                    text       = headerLabel,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = BrandBlue,
                    modifier   = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (mode != PickerMode.YEAR) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = ripple(bounded = true, color = BrandBlue),
                            ) {
                                mode = if (mode == PickerMode.DAY) PickerMode.MONTH else PickerMode.YEAR
                                yearWindowStart = viewYear - 5
                            } else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )

                val nextDisabled = when (mode) {
                    PickerMode.DAY -> atMaxMonth
                    PickerMode.MONTH -> atMaxYear
                    PickerMode.YEAR -> atMaxYearWindow
                }
                IconButton(
                    enabled = !nextDisabled,
                    onClick = {
                        when (mode) {
                            PickerMode.DAY -> if (viewMonth == 12) { viewMonth = 1; viewYear++ } else viewMonth++
                            PickerMode.MONTH -> viewYear++
                            PickerMode.YEAR -> yearWindowStart += 12
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next",
                        tint               = if (nextDisabled) NeutralLight else BrandBlue,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (mode) {
                PickerMode.YEAR -> YearGrid(
                    yearWindowStart = yearWindowStart,
                    selectedYear    = viewYear,
                    maxDate         = maxDate,
                    brandBlue       = BrandBlue,
                    neutralWhite    = NeutralWhite,
                    neutralBlack    = NeutralBlack,
                    neutralLight    = NeutralLight,
                    onSelectYear    = { year -> viewYear = year; mode = PickerMode.MONTH },
                )
                PickerMode.MONTH -> MonthGrid(
                    viewYear      = viewYear,
                    selectedMonth = viewMonth,
                    maxDate       = maxDate,
                    brandBlue     = BrandBlue,
                    neutralWhite  = NeutralWhite,
                    neutralBlack  = NeutralBlack,
                    neutralLight  = NeutralLight,
                    onSelectMonth = { month -> viewMonth = month; mode = PickerMode.DAY },
                )
                PickerMode.DAY -> DayGrid(
                    viewYear     = viewYear,
                    viewMonth    = viewMonth,
                    selected     = selected,
                    maxDate      = maxDate,
                    brandBlue    = BrandBlue,
                    brandYellow  = BrandYellow,
                    neutralWhite = NeutralWhite,
                    neutralBlack = NeutralBlack,
                    neutralMid   = NeutralMid,
                    neutralLight = NeutralLight,
                    onSelectDay  = { date -> selected = date },
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NeutralLight),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true),
                            onClick           = onDismiss,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "Cancel",
                        color      = NeutralMid,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandBlue)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true, color = NeutralWhite),
                            onClick           = { onConfirm(selected) },
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "Confirm",
                        color      = NeutralWhite,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayGrid(
    viewYear: Int,
    viewMonth: Int,
    selected: LocalDate,
    maxDate: LocalDate?,
    brandBlue: Color,
    brandYellow: Color,
    neutralWhite: Color,
    neutralBlack: Color,
    neutralMid: Color,
    neutralLight: Color,
    onSelectDay: (LocalDate) -> Unit,
) {
    Column {
        val dowOrder = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            dowOrder.forEach { dow ->
                Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        fontSize   = 11.sp,
                        color      = neutralMid,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        val ym          = YearMonth.of(viewYear, viewMonth)
        val firstDay    = ym.atDay(1)
        val startOffset = firstDay.dayOfWeek.value - 1
        val daysInMonth = ym.lengthOfMonth()
        val totalCells  = startOffset + daysInMonth
        val rows        = (totalCells + 6) / 7

        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val day       = cellIndex - startOffset + 1
                    val isValid   = day in 1..daysInMonth
                    val date      = if (isValid) LocalDate.of(viewYear, viewMonth, day) else null
                    val isSelected = date == selected
                    val isToday    = date == LocalDate.now()
                    val isDisabled = date != null && maxDate != null && date.isAfter(maxDate)
                    val isSelectable = isValid && !isDisabled

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> brandBlue
                                    isToday    -> brandYellow.copy(alpha = 0.25f)
                                    else       -> Color.Transparent
                                }
                            )
                            .then(
                                if (isSelectable) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = ripple(bounded = true, color = brandBlue),
                                ) { onSelectDay(date!!) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isValid) {
                            Text(
                                text       = day.toString(),
                                fontSize   = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color      = when {
                                    isDisabled -> neutralLight
                                    isSelected -> neutralWhite
                                    isToday    -> brandBlue
                                    else       -> neutralBlack
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    viewYear: Int,
    selectedMonth: Int,
    maxDate: LocalDate?,
    brandBlue: Color,
    neutralWhite: Color,
    neutralBlack: Color,
    neutralLight: Color,
    onSelectMonth: (Int) -> Unit,
) {
    Column {
        repeat(4) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(3) { col ->
                    val month = row * 3 + col + 1
                    val isSelected = month == selectedMonth
                    val isDisabled = maxDate != null &&
                        YearMonth.of(viewYear, month) > YearMonth.from(maxDate)
                    val monthLabel = java.time.Month.of(month)
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) brandBlue else Color.Transparent)
                            .then(
                                if (!isDisabled) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = ripple(bounded = true, color = brandBlue),
                                ) { onSelectMonth(month) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = monthLabel,
                            fontSize   = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color      = when {
                                isDisabled -> neutralLight
                                isSelected -> neutralWhite
                                else       -> neutralBlack
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(
    yearWindowStart: Int,
    selectedYear: Int,
    maxDate: LocalDate?,
    brandBlue: Color,
    neutralWhite: Color,
    neutralBlack: Color,
    neutralLight: Color,
    onSelectYear: (Int) -> Unit,
) {
    Column {
        repeat(4) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(3) { col ->
                    val year = yearWindowStart + row * 3 + col
                    val isSelected = year == selectedYear
                    val isDisabled = maxDate != null && year > maxDate.year

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) brandBlue else Color.Transparent)
                            .then(
                                if (!isDisabled) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = ripple(bounded = true, color = brandBlue),
                                ) { onSelectYear(year) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = year.toString(),
                            fontSize   = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color      = when {
                                isDisabled -> neutralLight
                                isSelected -> neutralWhite
                                else       -> neutralBlack
                            },
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 1: Replace the file with the content above**

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/shared/MonthPickerDialog.kt
git commit -m "feat: add Year/Month/Day drill-down navigation to the date picker"
```

---

### Task 3: Hold person-grouping — pure function (TDD)

**Files:**
- Create: `app/src/main/java/com/example/spendsync/ui/holds/HoldGrouping.kt`
- Test: `app/src/test/java/com/example/spendsync/ui/holds/HoldGroupingTest.kt`

**Interfaces:**
- Consumes: `HoldDto` from `com.example.spendsync.data.remote.model` (existing — fields `personName: String`, `amount: String`, `direction: String`).
- Produces: `internal data class PersonHoldSummary(val personName: String, val netAmount: Double, val holdCount: Int)`, `internal fun groupHoldsByPerson(holds: List<HoldDto>): List<PersonHoldSummary>`. Task 5 (HoldsScreen restructure) calls this directly.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.spendsync.ui.holds

import com.example.spendsync.data.remote.model.HoldDto
import org.junit.Assert.assertEquals
import org.junit.Test

private fun hold(
    id: String,
    personName: String,
    amount: String,
    direction: String,
    status: String = "pending",
) = HoldDto(
    id = id,
    userId = "u1",
    transactionId = "t-$id",
    direction = direction,
    personName = personName,
    amount = amount,
    expectedReturnDate = "2026-08-10T00:00:00.000Z",
    status = status,
    settledAt = null,
    createdAt = "2026-08-01T00:00:00.000Z",
    updatedAt = null,
)

class HoldGroupingTest {

    @Test
    fun `groups multiple holds for the same person and sums signed amounts`() {
        val holds = listOf(
            hold(id = "1", personName = "Manish", amount = "500", direction = "owed_to_me"),
            hold(id = "2", personName = "Manish", amount = "200", direction = "owed_to_me"),
        )

        val result = groupHoldsByPerson(holds)

        assertEquals(1, result.size)
        assertEquals("Manish", result[0].personName)
        assertEquals(700.0, result[0].netAmount, 0.001)
        assertEquals(2, result[0].holdCount)
    }

    @Test
    fun `nets owed_to_me and owed_by_me for the same person`() {
        val holds = listOf(
            hold(id = "1", personName = "Rahul", amount = "1000", direction = "owed_to_me"),
            hold(id = "2", personName = "Rahul", amount = "400", direction = "owed_by_me"),
        )

        val result = groupHoldsByPerson(holds)

        assertEquals(1, result.size)
        assertEquals(600.0, result[0].netAmount, 0.001)
        assertEquals(2, result[0].holdCount)
    }

    @Test
    fun `separates different people into separate groups`() {
        val holds = listOf(
            hold(id = "1", personName = "Manish", amount = "500", direction = "owed_to_me"),
            hold(id = "2", personName = "Rahul", amount = "300", direction = "owed_by_me"),
        )

        val result = groupHoldsByPerson(holds).associateBy { it.personName }

        assertEquals(2, result.size)
        assertEquals(500.0, result.getValue("Manish").netAmount, 0.001)
        assertEquals(-300.0, result.getValue("Rahul").netAmount, 0.001)
    }

    @Test
    fun `returns an empty list for no holds`() {
        assertEquals(emptyList<PersonHoldSummary>(), groupHoldsByPerson(emptyList()))
    }

    @Test
    fun `sorts groups alphabetically by person name`() {
        val holds = listOf(
            hold(id = "1", personName = "Zara", amount = "100", direction = "owed_to_me"),
            hold(id = "2", personName = "Amit", amount = "100", direction = "owed_to_me"),
        )

        val result = groupHoldsByPerson(holds)

        assertEquals(listOf("Amit", "Zara"), result.map { it.personName })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.ui.holds.HoldGroupingTest"`
Expected: FAIL — `groupHoldsByPerson`/`PersonHoldSummary` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.example.spendsync.ui.holds

import com.example.spendsync.data.remote.model.HoldDto

internal data class PersonHoldSummary(
    val personName: String,
    val netAmount: Double,
    val holdCount: Int,
)

/**
 * owed_to_me holds add to a person's net amount, owed_by_me holds subtract —
 * same signed-sum convention already used for Home's Hold Money aggregate.
 */
internal fun groupHoldsByPerson(holds: List<HoldDto>): List<PersonHoldSummary> {
    return holds
        .groupBy { it.personName }
        .map { (name, personHolds) ->
            val net = personHolds.sumOf { hold ->
                val amt = hold.amount.toDoubleOrNull() ?: 0.0
                if (hold.direction == "owed_to_me") amt else -amt
            }
            PersonHoldSummary(personName = name, netAmount = net, holdCount = personHolds.size)
        }
        .sortedBy { it.personName }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.ui.holds.HoldGroupingTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/holds/HoldGrouping.kt app/src/test/java/com/example/spendsync/ui/holds/HoldGroupingTest.kt
git commit -m "feat: add pure function to group holds by person"
```

---

### Task 4: HoldDetailScreen — per-person hold list with edit/settle/delete

**Files:**
- Create: `app/src/main/java/com/example/spendsync/ui/holds/HoldDetailScreen.kt`

**Interfaces:**
- Consumes: `HoldDto` (existing), `FinanceRepository.updateHold`/`deleteHold` (existing, `id, status?, personName?, expectedReturnDate?`), `HoldReminderWorker.cancel(context, holdId)`/`.schedule(context, holdId, personName, amount: Double, direction, expectedReturnDate: LocalDate)` (existing), `MonthPickerDialog` (Task 2, same public signature, unaffected by this task), `ToastHost`/`ToastMessage` (existing, same usage as `HoldsScreen.kt`), `formatInr` (existing).
- Produces: `HoldDetailScreen(personName: String, holds: List<HoldDto>, financeRepository: FinanceRepository, onBack: () -> Unit, onHoldsChanged: () -> Unit)`. Task 5 (HoldsScreen restructure) renders this when a person row is tapped.

The caller passes an already-filtered `holds` list (that person's holds only) — this screen does not fetch independently, it's a pure display+action layer over what its caller already has. `onHoldsChanged` is called after any successful mutation so the caller can refetch and pass an updated list back down.

- [ ] **Step 1: Write the screen**

```kotlin
package com.example.spendsync.ui.holds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.HoldReminderWorker
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.shared.MonthPickerDialog
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldDetailScreen(
    personName: String,
    holds: List<HoldDto>,
    financeRepository: FinanceRepository,
    onBack: () -> Unit,
    onHoldsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    val SemanticError = MaterialTheme.colorScheme.error

    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var holdToEdit by remember { mutableStateOf<HoldDto?>(null) }
    var holdToDelete by remember { mutableStateOf<HoldDto?>(null) }

    fun rescheduleReminder(hold: HoldDto, newPersonName: String, newDate: LocalDate) {
        HoldReminderWorker.cancel(context, hold.id)
        if (hold.status == "pending") {
            HoldReminderWorker.schedule(
                context = context,
                holdId = hold.id,
                personName = newPersonName,
                amount = hold.amount.toDoubleOrNull() ?: 0.0,
                direction = hold.direction,
                expectedReturnDate = newDate,
            )
        }
    }

    fun markSettled(hold: HoldDto) {
        scope.launch {
            when (val res = financeRepository.updateHold(id = hold.id, status = "settled")) {
                is AuthResult.Success -> {
                    HoldReminderWorker.cancel(context, hold.id)
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }

    fun saveEdit(hold: HoldDto, newPersonName: String, newDate: LocalDate) {
        scope.launch {
            val isoDate = "${newDate}T00:00:00.000Z"
            when (val res = financeRepository.updateHold(
                id = hold.id,
                personName = newPersonName,
                expectedReturnDate = isoDate,
            )) {
                is AuthResult.Success -> {
                    rescheduleReminder(hold, newPersonName, newDate)
                    holdToEdit = null
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }

    fun deleteHold(hold: HoldDto) {
        scope.launch {
            when (val res = financeRepository.deleteHold(hold.id)) {
                is AuthResult.Success -> {
                    HoldReminderWorker.cancel(context, hold.id)
                    holdToDelete = null
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        Column(modifier = Modifier.fillMaxSize().background(NeutralOffWhite)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
                }
                Spacer(Modifier.width(8.dp))
                Text(personName, fontSize = 20.sp, color = NeutralBlack)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(holds) { hold ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (hold.direction == "owed_to_me") "Owed to you" else "You owe",
                                    fontSize = 13.sp,
                                    color = NeutralMid,
                                )
                                Text(
                                    formatInr(hold.amount.toDoubleOrNull() ?: 0.0),
                                    fontSize = 16.sp,
                                    color = NeutralBlack,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Expected: ${hold.expectedReturnDate.take(10)} · ${hold.status}",
                                fontSize = 12.sp,
                                color = NeutralMid,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { holdToEdit = hold }) {
                                    Text("Edit")
                                }
                                if (hold.status == "pending") {
                                    Button(
                                        onClick = { markSettled(hold) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                    ) {
                                        Text("Mark as settled")
                                    }
                                }
                                TextButton(onClick = { holdToDelete = hold }) {
                                    Text("Delete", color = SemanticError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    holdToEdit?.let { hold ->
        EditHoldDialog(
            hold = hold,
            onDismiss = { holdToEdit = null },
            onSave = { newPersonName, newDate -> saveEdit(hold, newPersonName, newDate) },
        )
    }

    holdToDelete?.let { hold ->
        BasicAlertDialog(
            onDismissRequest = { holdToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Delete this hold?", fontSize = 18.sp, color = NeutralBlack)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This removes tracking for ${formatInr(hold.amount.toDoubleOrNull() ?: 0.0)} with $personName. This can't be undone.",
                        fontSize = 13.sp,
                        color = NeutralMid,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { holdToDelete = null }) { Text("Cancel") }
                        TextButton(onClick = { deleteHold(hold) }) { Text("Delete", color = SemanticError) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditHoldDialog(
    hold: HoldDto,
    onDismiss: () -> Unit,
    onSave: (personName: String, expectedReturnDate: LocalDate) -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val BrandBlue = MaterialTheme.colorScheme.primary

    var personName by remember { mutableStateOf(hold.personName) }
    var expectedDate by remember {
        mutableStateOf(
            try {
                java.time.ZonedDateTime.parse(hold.expectedReturnDate).toLocalDate()
            } catch (e: Exception) {
                LocalDate.now()
            }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Edit hold", fontSize = 18.sp, color = NeutralBlack)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = NeutralLight,
                        cursorColor = BrandBlue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeutralLight.copy(alpha = 0.3f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Expected return: $expectedDate",
                        color = NeutralBlack,
                    )
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text("Change date")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = { onSave(personName, expectedDate) },
                        enabled = personName.isNotBlank(),
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showDatePicker) {
        MonthPickerDialog(
            current = expectedDate,
            onConfirm = { picked -> expectedDate = picked; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/holds/HoldDetailScreen.kt
git commit -m "feat: add hold detail screen with edit, settle, and delete actions"
```

---

### Task 5: HoldsScreen — restructure into a person-grouped list

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/holds/HoldsScreen.kt`

**Interfaces:**
- Consumes: `groupHoldsByPerson`/`PersonHoldSummary` (Task 3), `HoldDetailScreen` (Task 4), `FinanceRepository.getHolds` (existing, unchanged).
- Produces: `HoldsScreen(financeRepository, onBack)` — public signature unchanged, so `MainScreen.kt`'s existing call site needs no changes.

- [ ] **Step 1: Replace the file**

```kotlin
package com.example.spendsync.ui.holds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.utils.formatInr

@Composable
fun HoldsScreen(
    financeRepository: FinanceRepository,
    onBack: () -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant

    var holds by remember { mutableStateOf<List<HoldDto>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        when (val res = financeRepository.getHolds()) {
            is AuthResult.Success -> holds = res.data
            is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
        }
    }

    val currentSelectedPerson = selectedPerson
    if (currentSelectedPerson != null) {
        HoldDetailScreen(
            personName = currentSelectedPerson,
            holds = holds.filter { it.personName == currentSelectedPerson },
            financeRepository = financeRepository,
            onBack = { selectedPerson = null },
            onHoldsChanged = { refreshKey++ },
        )
        return
    }

    val personSummaries = remember(holds) { groupHoldsByPerson(holds) }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        Column(modifier = Modifier.fillMaxSize().background(NeutralOffWhite)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
                }
                Spacer(Modifier.width(8.dp))
                Text("Holds", fontSize = 20.sp, color = NeutralBlack)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(personSummaries) { person ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { selectedPerson = person.personName },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(person.personName, fontSize = 16.sp, color = NeutralBlack)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${person.holdCount} hold${if (person.holdCount == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = NeutralMid,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (person.netAmount >= 0) "Owed to you" else "You owe",
                                        fontSize = 11.sp,
                                        color = NeutralMid,
                                    )
                                    Text(
                                        formatInr(kotlin.math.abs(person.netAmount)),
                                        fontSize = 16.sp,
                                        color = NeutralBlack,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = NeutralMid,
                                    modifier = Modifier.padding(2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Note: this needs `import androidx.compose.ui.unit.sp` — check it's present (it was in the original file); add if missing.

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass, including the 5 new `HoldGroupingTest` cases from Task 3.

- [ ] **Step 4: Manually verify on-device**

Run: `/run` (or `./gradlew installDebug` + launch manually)

1. Open Home. Confirm the balance card now shows Net Balance as the big headline, with Total Balance and Hold Money as the two sub-stats below.
2. Confirm the Income/Expense tiles show the whole month's totals, not just today's — add a transaction dated earlier this month (via Edit on an existing one, or check against a known multi-transaction month) and confirm both tiles reflect it.
3. Tap the calendar icon. Confirm it opens in Day mode as before. Tap the month/year header label — confirm it switches to a 12-month grid for the current year. Tap the year label in Month mode — confirm it switches to a scrollable/paged year grid. Tap a year, then a month, then a day — confirm each drill-down step works and the final Confirm still sets the date as before.
4. Tap "Hold Money" on Home. Confirm the Holds screen now shows one row per person (not per hold), with a net amount and hold count.
5. Tap a person. Confirm the detail screen lists that person's individual holds with Edit / Mark as settled / Delete actions.
6. Edit a hold's name and/or date, save, confirm it updates and the list reflects the change.
7. Delete a hold, confirm the confirmation dialog appears and deletion works.
8. Mark a hold as settled from the detail screen, confirm it updates correctly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/holds/HoldsScreen.kt
git commit -m "feat: restructure Holds screen into a person-grouped list"
```
