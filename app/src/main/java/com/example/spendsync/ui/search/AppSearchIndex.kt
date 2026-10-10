package com.example.spendsync.ui.search

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.spendsync.R
import com.example.spendsync.navigation.BottomNavItem
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.SettingsPage

/** Where a search result takes the user. */
sealed interface SearchDest {
    /** A Settings page (the Profile tab, with that page open). */
    data class Settings(val page: SettingsPage) : SearchDest
    data class Tab(val route: String) : SearchDest
    data object Holds : SearchDest
    data class Add(val income: Boolean) : SearchDest
}

/** One searchable place in the app. [where] is the breadcrumb shown under the title ("Settings › Appearance"). */
data class AppSearchEntry(
    val title: String,
    val where: String,
    val keywords: List<String>,
    val icon: ImageVector,
    val dest: SearchDest,
)

private fun s(@StringRes id: Int) = tr(id)

/**
 * Every setting and every screen, registered once, so nothing in the app is hidden from search. Add a row here when a
 * new setting or screen is added. Titles come from the same strings the screens use, so they follow the language.
 */
fun appSearchEntries(): List<AppSearchEntry> {
    val out = mutableListOf<AppSearchEntry>()
    fun setting(page: SettingsPage, @StringRes title: Int, vararg keys: String) =
        out.add(AppSearchEntry(s(title), s(R.string.settings) + " › " + page.title, keys.toList() + page.title, page.icon, SearchDest.Settings(page)))

    // Account
    setting(SettingsPage.Account, R.string.edit_profile, "name", "email", "profile")
    setting(SettingsPage.Account, R.string.sign_out_2, "logout", "log out")
    setting(SettingsPage.Account, R.string.delete_account, "remove account")
    setting(SettingsPage.Account, R.string.cloud_sync, "sync", "cloud")
    setting(SettingsPage.Account, R.string.member_since, "joined")
    setting(SettingsPage.Account, R.string.user_id, "id")
    // Appearance
    setting(SettingsPage.Appearance, R.string.theme, "dark", "light", "night", "mode")
    setting(SettingsPage.Appearance, R.string.accent_colour, "color", "colour", "accent")
    setting(SettingsPage.Appearance, R.string.language, "translate", "hindi", "spanish", "french", "german", "english")
    setting(SettingsPage.Appearance, R.string.date_format_2, "date")
    // Notifications
    setting(SettingsPage.Notifications, R.string.push_notifications_2, "alerts", "reminders")
    setting(SettingsPage.Notifications, R.string.email_notifications, "mail", "summaries")
    // Privacy
    setting(SettingsPage.Privacy, R.string.hide_large_amounts, "mask", "hide", "pin", "privacy")
    setting(SettingsPage.Privacy, R.string.change_pin, "pin", "passcode")
    setting(SettingsPage.Privacy, R.string.visibility_duration, "timer", "lock", "unlock")
    // Assistant
    setting(SettingsPage.Assistant, R.string.assistant_title, "ai", "chat")
    setting(SettingsPage.Assistant, R.string.asst_group_use, "tools", "permissions")
    setting(SettingsPage.Assistant, R.string.asst_group_model, "model", "gemini", "groq")
    setting(SettingsPage.Assistant, R.string.asst_group_style, "short", "detailed", "length")
    setting(SettingsPage.Assistant, R.string.asst_group_tone, "friendly", "professional", "simple")
    setting(SettingsPage.Assistant, R.string.asst_group_instructions, "custom", "prompt")
    setting(SettingsPage.Assistant, R.string.asst_group_history, "clear chat", "delete chat")
    setting(SettingsPage.Appearance, R.string.cc_title, "custom", "colors", "colours", "background", "icons", "palette")
    setting(SettingsPage.Reports, R.string.rp_title, "support", "ticket", "problem", "report")
    setting(SettingsPage.Data, R.string.bills_title, "bill", "bills", "receipt", "storage", "invoice")
    setting(SettingsPage.Trash, R.string.trash_title, "trash", "deleted", "restore", "undo", "bin", "recycle")
    // Planify
    setting(SettingsPage.Planify, R.string.pl_set_alerts, "limit", "warn", "budget")
    setting(SettingsPage.Planify, R.string.pl_set_daily, "summary", "evening")
    setting(SettingsPage.Planify, R.string.pl_set_salary, "salary", "income")
    // Animations
    setting(SettingsPage.Animations, R.string.an_all, "motion", "reduce", "effects")
    listOf(R.string.an_counts, R.string.an_entrance, R.string.an_transitions, R.string.an_typing, R.string.an_skeleton, R.string.an_press, R.string.an_charts)
        .forEach { setting(SettingsPage.Animations, it, "animation", "motion") }
    // Auto capture
    setting(SettingsPage.AutoCapture, R.string.auto_capture, "notifications", "upi", "bank", "detect")
    setting(SettingsPage.AutoCapture, R.string.apps_to_watch, "google pay", "phonepe", "paytm")
    // Data
    setting(SettingsPage.Data, R.string.auto_backup_2, "backup", "cloud")
    setting(SettingsPage.Data, R.string.export_data, "csv", "pdf", "download", "export")
    setting(SettingsPage.Data, R.string.clear_local_data, "cache", "reset")
    // About
    setting(SettingsPage.About, R.string.privacy_policy_2, "legal", "terms")
    setting(SettingsPage.About, R.string.frequently_asked, "faq", "help", "support", "contact")

    // Screens
    fun screen(@StringRes title: Int, icon: ImageVector, dest: SearchDest, vararg keys: String) =
        out.add(AppSearchEntry(s(title), s(R.string.search_screens), keys.toList(), icon, dest))
    screen(R.string.home, Icons.Default.Home, SearchDest.Tab(BottomNavItem.Home.route), "overview", "balance", "recent")
    screen(R.string.analytics, Icons.Default.BarChart, SearchDest.Tab(BottomNavItem.Analytics.route), "chart", "trend", "insights")
    screen(R.string.pl_title, Icons.Default.PieChart, SearchDest.Tab(BottomNavItem.Planify.route), "plan", "budget", "bucket", "salary")
    screen(R.string.holds, Icons.Default.Handshake, SearchDest.Holds, "lent", "borrowed", "owe", "remind")
    screen(R.string.assistant_title, Icons.Default.AutoAwesome, SearchDest.Tab(BottomNavItem.Assistant.route), "ai", "ask", "chat")
    screen(R.string.add_transaction, Icons.Default.Add, SearchDest.Add(income = false), "expense", "spend", "new")
    screen(R.string.income, Icons.Default.Add, SearchDest.Add(income = true), "salary", "money in", "credit")
    return out
}

/** Entries whose title, keywords or location contain every word typed. */
fun searchApp(entries: List<AppSearchEntry>, query: String): List<AppSearchEntry> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.filter { e ->
        val haystack = (listOf(e.title, e.where) + e.keywords).joinToString(" ")
        words.all { haystack.contains(it, ignoreCase = true) }
    }.distinctBy { it.title + it.where }
}
