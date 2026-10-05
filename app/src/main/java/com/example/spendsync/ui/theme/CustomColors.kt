package com.example.spendsync.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The user's own colours (Settings -> Appearance -> Custom colours). Each role is optional: a role left empty keeps
 * the theme's colour, or, for text and icons, a readable one worked out from the background. Stored on this phone only.
 */
data class CustomColors(
    val enabled: Boolean = false,
    val accent: Int? = null,
    val background: Int? = null,
    val cards: Int? = null,
    val text: Int? = null,
    val icons: Int? = null,
    val income: Int? = null,
    val expense: Int? = null,
) {
    companion object {
        /** Hand-picked sets that go together; choosing one fills every role. */
        val presets: List<Pair<String, CustomColors>> = listOf(
            "Midnight" to CustomColors(true, 0xFF8AB4F8.toInt(), 0xFF0B1020.toInt(), 0xFF151B2E.toInt(), 0xFFEAF0FF.toInt(), 0xFF9AA6C4.toInt(), 0xFF5EE0A0.toInt(), 0xFFFF7A8A.toInt()),
            "Sand" to CustomColors(true, 0xFFB45309.toInt(), 0xFFF6EFE3.toInt(), 0xFFFFFAF0.toInt(), 0xFF2B2118.toInt(), 0xFF7A6A58.toInt(), 0xFF3F7D3A.toInt(), 0xFFB3392F.toInt()),
            "Forest" to CustomColors(true, 0xFF34D399.toInt(), 0xFF0F1A14.toInt(), 0xFF16251C.toInt(), 0xFFE6F4EA.toInt(), 0xFF8FB39C.toInt(), 0xFF6EE7A8.toInt(), 0xFFFB923C.toInt()),
            "Rose" to CustomColors(true, 0xFFBE185D.toInt(), 0xFFFFF1F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF3B1224.toInt(), 0xFF8E5A70.toInt(), 0xFF15803D.toInt(), 0xFFBE123C.toInt()),
            "Ocean" to CustomColors(true, 0xFF0EA5E9.toInt(), 0xFFEFF8FC.toInt(), 0xFFFFFFFF.toInt(), 0xFF0B2A3A.toInt(), 0xFF5B7C8D.toInt(), 0xFF0F9D6E.toInt(), 0xFFD9480F.toInt()),
            "Mono" to CustomColors(true, 0xFF111111.toInt(), 0xFFF5F5F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF6B6B6B.toInt(), 0xFF2E7D32.toInt(), 0xFFC62828.toInt()),
        )
    }
}

val LocalCustomColors = compositionLocalOf { CustomColors() }

private fun readableOn(bg: Color) = if (bg.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF5F5F5)

/** The theme's colours with the user's choices on top. Anything not chosen is left alone or derived to stay readable. */
fun ColorScheme.withCustom(c: CustomColors): ColorScheme {
    if (!c.enabled) return this
    val bg = c.background?.let { Color(it) }
    val cards = c.cards?.let { Color(it) }
    val accent = c.accent?.let { Color(it) }
    // Text defaults to whatever reads well on the chosen background (or cards), so a dark background never gets dark text.
    val text = c.text?.let { Color(it) } ?: (bg ?: cards)?.let { readableOn(it) }
    val icons = c.icons?.let { Color(it) } ?: text?.copy(alpha = 0.72f)
    return copy(
        primary = accent ?: primary,
        onPrimary = accent?.let { readableOn(it) } ?: onPrimary,
        primaryContainer = accent?.copy(alpha = 0.18f) ?: primaryContainer,
        tertiary = accent ?: tertiary,
        background = bg ?: background,
        surface = cards ?: surface,
        surfaceVariant = (cards ?: bg)?.let { base -> if (base.luminance() > 0.5f) androidx.compose.ui.graphics.lerp(base, Color.Black, 0.06f) else androidx.compose.ui.graphics.lerp(base, Color.White, 0.08f) } ?: surfaceVariant,
        onBackground = text ?: onBackground,
        onSurface = text ?: onSurface,
        onSurfaceVariant = icons ?: onSurfaceVariant,
        outline = icons?.copy(alpha = 0.6f) ?: outline,
    )
}

/** Income and expense colours honour the user's choice when they made one. */
@Composable
internal fun customIncome(): Color? = LocalCustomColors.current.takeIf { it.enabled }?.income?.let { Color(it) }

@Composable
internal fun customExpense(): Color? = LocalCustomColors.current.takeIf { it.enabled }?.expense?.let { Color(it) }
