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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerDialog(
    current: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    // When set, days (and months) after this are shown but disabled — used by
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

            // Month / Year navigation row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = {
                    if (viewMonth == 1) { viewMonth = 12; viewYear-- } else viewMonth--
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Prev month",
                        tint               = BrandBlue,
                    )
                }

                val monthName = java.time.Month.of(viewMonth)
                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
                    .replaceFirstChar { it.uppercase() }

                Text(
                    text       = "$monthName $viewYear",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NeutralBlack,
                )

                val atMaxMonth = maxDate != null &&
                    YearMonth.of(viewYear, viewMonth) >= YearMonth.from(maxDate)
                IconButton(
                    enabled = !atMaxMonth,
                    onClick = {
                        if (viewMonth == 12) { viewMonth = 1; viewYear++ } else viewMonth++
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint               = if (atMaxMonth) NeutralLight else BrandBlue,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Day-of-week headers (Mon..Sun)
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
                            color      = NeutralMid,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Day grid
            val ym          = YearMonth.of(viewYear, viewMonth)
            val firstDay    = ym.atDay(1)
            val startOffset = firstDay.dayOfWeek.value - 1 // Mon=1 -> offset 0
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
                                        isSelected -> BrandBlue
                                        isToday    -> BrandYellow.copy(alpha = 0.25f)
                                        else       -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isSelectable) Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = ripple(bounded = true, color = BrandBlue),
                                    ) { selected = date!! }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isValid) {
                                Text(
                                    text       = day.toString(),
                                    fontSize   = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color      = when {
                                        isDisabled -> NeutralLight
                                        isSelected -> NeutralWhite
                                        isToday    -> BrandBlue
                                        else       -> NeutralBlack
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NeutralLight),
            )

            Spacer(Modifier.height(12.dp))

            // Action row
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
