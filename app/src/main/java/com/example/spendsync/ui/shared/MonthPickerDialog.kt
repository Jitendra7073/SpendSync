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
