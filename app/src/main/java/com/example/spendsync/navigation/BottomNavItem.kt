package com.example.spendsync.navigation

import androidx.annotation.StringRes
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Each item that appears in the bottom navigation bar.
 *
 * [isFab] marks the centre "+" button — it renders differently (no label, elevated circle).
 */
sealed class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val isFab: Boolean = false,
    /** False for items that open something (the assistant) instead of switching to a page. */
    val selectable: Boolean = true,
) {
    val label: String get() = tr(labelRes)

    object Home : BottomNavItem(
        route = "tab_home",
        labelRes = R.string.home,
        icon  = Icons.Default.Home,
    )

    object Analytics : BottomNavItem(
        route = "tab_analytics",
        labelRes = R.string.analytics,
        icon  = Icons.Default.BarChart,
    )

    object AddTransaction : BottomNavItem(
        route  = "tab_add",
        labelRes = R.string.add,
        icon   = Icons.Default.Add,
        isFab  = true,
    )

    /** Opens the assistant chat; not a page, so it is never highlighted as the current tab. */
    object Assistant : BottomNavItem(
        route = "tab_assistant",
        labelRes = R.string.assistant_title,
        icon  = Icons.Default.AutoAwesome,
        selectable = false,
    )

    /** Still a page (reachable from the assistant), but hidden from the bottom bar. */
    object Budget : BottomNavItem(
        route = "tab_budget",
        labelRes = R.string.budget,
        icon  = Icons.Default.Wallet,
    )

    object Profile : BottomNavItem(
        route = "tab_profile",
        labelRes = R.string.profile,
        icon  = Icons.Default.AccountCircle,
    )

    companion object {
        // MUST stay `by lazy`. If this list is built eagerly during the sealed
        // class's static initialization, it reads the object subclasses (Home,
        // Analytics, …) while they are *still initializing* — e.g. the first time
        // anything touches `BottomNavItem.Home`, the JVM initializes the parent
        // class first, which would run this initializer before Home's INSTANCE is
        // assigned. The list would then contain `null` entries, causing a
        // NullPointerException in SpendSyncBottomBar (item.isFab). Deferring with
        // `by lazy` guarantees every object is fully constructed before use.
        val all by lazy { listOf(Home, Analytics, Profile, Assistant, AddTransaction) }
    }
}
