package com.example.spendsync.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The calendar/date picker and the global-search trigger, grouped into one
 * pill so they read as a single unit — used on every top-level tab (Home,
 * Analytics, Budget) so the control stays in the same place across screens.
 */
@Composable
fun TopBarDateSearchGroup(
    selectedDate: LocalDate,
    onCalendarClick: () -> Unit,
    onSearchClick: () -> Unit,
    contentColor: Color,
    groupBackgroundColor: Color,
    modifier: Modifier = Modifier,
    dateFormatPattern: String = "d MMM yyyy",
) {
    val dateFormatter = remember(dateFormatPattern) {
        DateTimeFormatter.ofPattern(dateFormatPattern, Locale.getDefault())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(groupBackgroundColor)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onCalendarClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = selectedDate.format(dateFormatter),
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Select Date",
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onSearchClick)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
