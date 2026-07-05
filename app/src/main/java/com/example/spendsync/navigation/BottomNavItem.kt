package com.example.spendsync.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
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
    val label: String,
    val icon: ImageVector,
    val isFab: Boolean = false,
) {
    object Home : BottomNavItem(
        route = "tab_home",
        label = "Home",
        icon  = Icons.Default.Home,
    )

    object Analytics : BottomNavItem(
        route = "tab_analytics",
        label = "Analytics",
        icon  = Icons.Default.BarChart,
    )

    object AddTransaction : BottomNavItem(
        route  = "tab_add",
        label  = "Add",
        icon   = Icons.Default.Add,
        isFab  = true,
    )

    object Budget : BottomNavItem(
        route = "tab_budget",
        label = "Budget",
        icon  = Icons.Default.Wallet,
    )

    object Profile : BottomNavItem(
        route = "tab_profile",
        label = "Profile",
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
        val all by lazy { listOf(Home, Analytics, AddTransaction, Budget, Profile) }
    }
}
