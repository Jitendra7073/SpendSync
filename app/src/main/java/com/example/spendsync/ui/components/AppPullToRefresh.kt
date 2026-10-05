package com.example.spendsync.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr

/**
 * Pull down a little and let go: refresh. Pull down a long way and let go: [onLongPull] (open search). The long pull
 * is measured from the finger's raw travel at the top of the list, and a small label + tick tells the user the
 * long pull is armed before they let go. Without [onLongPull] it is a normal pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLongPull: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val longPx = with(LocalDensity.current) { 340.dp.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val connection = remember(longPx, onLongPull != null) {
        object : NestedScrollConnection {
            // Sees what the list could not use (the list is at its top and the finger keeps going down), without
            // taking it, so the normal refresh indicator still works.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && onLongPull != null) {
                    pulled = (pulled + available.y).coerceAtLeast(0f)
                    val now = pulled >= longPx
                    if (now && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    armed = now
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                armed = false
                return Velocity.Zero
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // The refresh callback fires on release, before the gesture state above is reset.
            if (pulled >= longPx && onLongPull != null) onLongPull() else onRefresh()
        },
        modifier = modifier,
    ) {
        Box(Modifier.nestedScroll(connection)) { content() }
        AnimatedVisibility(
            visible = armed,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier.statusBarsPadding().padding(top = 8.dp).clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(tr(R.string.pull_release_search), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
            }
        }
    }
}
