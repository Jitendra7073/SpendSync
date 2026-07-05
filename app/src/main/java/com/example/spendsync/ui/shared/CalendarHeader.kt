package com.example.spendsync.ui.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
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

// CalendarHeader
// Renders:  <- | DD Month, YYYY | ->
// Tapping the centre label opens the full-month calendar dialog.

/**
 * @param state   Shared [DateFilterState] — mutated in-place.
 * @param onWhite When true the arrows and text are white (for use inside the
 *                blue header band); when false they use the brand/dark palette.
 */
@Composable
fun CalendarHeader(
    state: DateFilterState,
    onWhite: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var slideDir   by remember { mutableStateOf(1) } // +1 = right, -1 = left

    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val BrandBlue = MaterialTheme.colorScheme.primary

    val textColor = if (onWhite) NeutralWhite else NeutralBlack
    val iconTint  = if (onWhite) NeutralWhite else BrandBlue
    val labelBg   = if (onWhite) NeutralWhite.copy(alpha = 0.15f) else BrandBlue.copy(alpha = 0.08f)

    Row(
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left arrow
        IconButton(
            onClick = {
                slideDir = -1
                state.prevMonth()
            },
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint               = iconTint,
                modifier           = Modifier.size(26.dp),
            )
        }

        // Centre label: DD Month, YYYY  (animates on month change)
        AnimatedContent(
            targetState    = state.selectedDate,
            transitionSpec = {
                slideInHorizontally { w -> slideDir * w } togetherWith
                    slideOutHorizontally { w -> -slideDir * w }
            },
            label = "cal_label",
        ) { date ->
            val day   = date.dayOfMonth.toString().padStart(2, '0')
            val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
            val year  = date.year

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(labelBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(
                            bounded = true,
                            color   = if (onWhite) NeutralWhite else BrandBlue,
                        ),
                        onClick = { state.showMonthPicker = true },
                    )
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "$day $month, $year",
                    color      = textColor,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Right arrow
        IconButton(
            onClick = {
                slideDir = 1
                state.nextMonth()
            },
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint               = iconTint,
                modifier           = Modifier.size(26.dp),
            )
        }
    }
}

// Full-month picker dialog
