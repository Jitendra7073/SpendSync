package com.example.spendsync.ui.shared

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Text
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class PickerMode { YEAR, MONTH, DAY }

/**
 * Date picker. Starts on the day grid; tap the month/year title to jump to a month or year,
 * tap a choice to drop back down. Every colour is a theme token with a paired "on" colour, so
 * numbers stay readable in light, dark and every accent: selected = accent circle with
 * on-accent text, today = accent ring, unavailable = dimmed.
 *
 * [maxDate], when set, greys out anything after it (used so a transaction can't be dated in
 * the future).
 */
@Composable
fun MonthPickerDialog(
    current: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    maxDate: LocalDate? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var viewYear by rememberSaveable { mutableIntStateOf(current.year) }
    var viewMonth by rememberSaveable { mutableIntStateOf(current.monthValue) }
    var selected by remember { mutableStateOf(current) }
    var mode by rememberSaveable { mutableStateOf(PickerMode.DAY) }
    var yearWindowStart by rememberSaveable { mutableIntStateOf(current.year - 5) }
    val locale = Locale.getDefault()

    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.pick_a_date),
        icon = Icons.Default.CalendarMonth,
        primary = DialogAction(tr(R.string.select), { onConfirm(selected) }),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        val atMaxMonth = maxDate != null && YearMonth.of(viewYear, viewMonth) >= YearMonth.from(maxDate)
        val atMaxYear = maxDate != null && viewYear >= maxDate.year
        val atMaxWindow = maxDate != null && yearWindowStart + 11 >= maxDate.year
        val nextDisabled = when (mode) {
            PickerMode.DAY -> atMaxMonth
            PickerMode.MONTH -> atMaxYear
            PickerMode.YEAR -> atMaxWindow
        }
        val headerLabel = when (mode) {
            PickerMode.DAY -> Month.of(viewMonth).getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " $viewYear"
            PickerMode.MONTH -> "$viewYear"
            PickerMode.YEAR -> "$yearWindowStart – ${yearWindowStart + 11}"
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AppIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr(R.string.previous), onClick = {
                when (mode) {
                    PickerMode.DAY -> if (viewMonth == 1) { viewMonth = 12; viewYear-- } else viewMonth--
                    PickerMode.MONTH -> viewYear--
                    PickerMode.YEAR -> yearWindowStart -= 12
                }
            })
            AppButton(
                text = headerLabel,
                onClick = {
                    if (mode != PickerMode.YEAR) {
                        mode = if (mode == PickerMode.DAY) PickerMode.MONTH else PickerMode.YEAR
                        yearWindowStart = viewYear - 5
                    }
                },
                variant = ButtonVariant.Text,
                size = ButtonSize.Small,
                trailingIcon = if (mode != PickerMode.YEAR) Icons.Default.ArrowDropDown else null,
                enabled = mode != PickerMode.YEAR,
            )
            AppIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr(R.string.next), enabled = !nextDisabled, onClick = {
                when (mode) {
                    PickerMode.DAY -> if (viewMonth == 12) { viewMonth = 1; viewYear++ } else viewMonth++
                    PickerMode.MONTH -> viewYear++
                    PickerMode.YEAR -> yearWindowStart += 12
                }
            })
        }

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = mode,
            transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f)) togetherWith (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f)) },
            label = "picker_mode",
        ) { m ->
            when (m) {
                PickerMode.YEAR -> ChoiceGrid(
                    labels = (0 until 12).map { (yearWindowStart + it).toString() },
                    selectedIndex = (viewYear - yearWindowStart).takeIf { it in 0..11 },
                    isDisabled = { maxDate != null && yearWindowStart + it > maxDate.year },
                    onPick = { viewYear = yearWindowStart + it; mode = PickerMode.MONTH },
                )
                PickerMode.MONTH -> ChoiceGrid(
                    labels = (1..12).map { Month.of(it).getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { c -> c.uppercase() } },
                    selectedIndex = viewMonth - 1,
                    isDisabled = { maxDate != null && YearMonth.of(viewYear, it + 1) > YearMonth.from(maxDate) },
                    onPick = { viewMonth = it + 1; mode = PickerMode.DAY },
                )
                PickerMode.DAY -> DayGrid(viewYear, viewMonth, selected, maxDate, locale) { selected = it }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                selected.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val today = LocalDate.now()
            val todayAllowed = maxDate == null || !today.isAfter(maxDate)
            AppButton(
                text = tr(R.string.today),
                onClick = { selected = today; viewYear = today.year; viewMonth = today.monthValue; mode = PickerMode.DAY },
                variant = ButtonVariant.Tonal,
                size = ButtonSize.Small,
                enabled = todayAllowed,
            )
        }
    }
}

@Composable
private fun DayGrid(
    viewYear: Int,
    viewMonth: Int,
    selected: LocalDate,
    maxDate: LocalDate?,
    locale: Locale,
    onSelectDay: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ym = YearMonth.of(viewYear, viewMonth)
    val startOffset = ym.atDay(1).dayOfWeek.value - 1 // weeks start on Monday
    val daysInMonth = ym.lengthOfMonth()
    val rows = (startOffset + daysInMonth + 6) / 7
    val today = remember { LocalDate.now() }

    Column {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Box(Modifier.weight(1f).heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, locale),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = row * 7 + col - startOffset + 1
                    if (day !in 1..daysInMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = LocalDate.of(viewYear, viewMonth, day)
                        DayCell(
                            day = day,
                            isSelected = date == selected,
                            isToday = date == today,
                            isDisabled = maxDate != null && date.isAfter(maxDate),
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectDay(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, isSelected: Boolean, isToday: Boolean, isDisabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (isSelected) scheme.primary else Color.Transparent, tween(160), label = "day_bg")
    val fg by animateColorAsState(
        when {
            isSelected -> scheme.onPrimary
            isDisabled -> scheme.onSurface.copy(alpha = 0.32f)
            isToday -> scheme.primary
            else -> scheme.onSurface
        },
        tween(160), label = "day_fg",
    )
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier)
            .semantics { this.selected = isSelected }
            .then(if (!isDisabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            fontSize = 14.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = fg,
        )
    }
}

/** 3×4 grid used for both the month and the year steps. */
@Composable
private fun ChoiceGrid(
    labels: List<String>,
    selectedIndex: Int?,
    isDisabled: (Int) -> Boolean,
    onPick: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { col ->
                    val i = row * 3 + col
                    val isSel = i == selectedIndex
                    val disabled = isDisabled(i)
                    val bg by animateColorAsState(if (isSel) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.5f), tween(160), label = "choice_bg")
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .semantics { this.selected = isSel }
                            .then(if (!disabled) Modifier.clickable(role = Role.Button) { onPick(i) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            labels[i],
                            fontSize = 14.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSel -> scheme.onPrimary
                                disabled -> scheme.onSurface.copy(alpha = 0.32f)
                                else -> scheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
