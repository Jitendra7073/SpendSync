package com.example.spendsync.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val SpendSyncLightColors = lightColorScheme(
    primary            = BrandBlue,
    onPrimary          = NeutralWhite,
    primaryContainer   = BrandBlueSurface,
    onPrimaryContainer = NeutralWhite,
    secondary          = BrandYellow,
    onSecondary        = NeutralBlack,
    tertiary           = BrandBlueDark,
    background         = NeutralOffWhite,
    onBackground       = NeutralBlack,
    surface            = NeutralWhite,
    onSurface          = NeutralBlack,
    surfaceVariant     = NeutralLight,
    onSurfaceVariant   = NeutralDark,
    error              = SemanticError,
    onError            = NeutralWhite,
    outline            = NeutralMid,
)

private val DarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F2937)

private val SpendSyncDarkColors = darkColorScheme(
    primary            = BrandBlueSurface,
    onPrimary          = NeutralWhite,
    primaryContainer   = BrandBlueDark,
    onPrimaryContainer = NeutralWhite,
    secondary          = BrandYellow,
    onSecondary        = NeutralBlack,
    tertiary           = BrandBlue,
    background         = NeutralBlack,
    onBackground       = NeutralOffWhite,
    surface            = NeutralDark,
    onSurface          = NeutralOffWhite,
    surfaceVariant     = DarkSurfaceVariant,
    onSurfaceVariant   = NeutralLight,
    error              = SemanticError,
    onError            = NeutralWhite,
    outline            = NeutralMid,
)

@Composable
fun SpendSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColorName: String = "Brand Blue",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val primaryColor = when (accentColorName) {
        "Kakariki Green" -> Color(0xFF4D7C0F) // Olive Green
        "Emerald Green"  -> Color(0xFF059669) // Emerald
        "Crimson Red"    -> Color(0xFFDC2626) // Crimson Red
        "Coral Orange"   -> Color(0xFFF97316) // Coral Orange
        else             -> BrandBlue
    }

    val backgroundColor = when (accentColorName) {
        "Kakariki Green" -> KakarikiBg // 0xFFE9EDDF
        else             -> NeutralOffWhite
    }

    val onBackgroundColor = when (accentColorName) {
        "Kakariki Green" -> KakarikiOnBg // 0xFF2E3324
        else             -> NeutralBlack
    }

    val surfaceColor = when (accentColorName) {
        "Kakariki Green" -> Color(0xFFF4F6F0) // Matching soft warm white
        else             -> NeutralWhite
    }

    val baseLightScheme = SpendSyncLightColors.copy(
        primary = primaryColor,
        primaryContainer = primaryColor.copy(alpha = 0.15f),
        background = backgroundColor,
        onBackground = onBackgroundColor,
        surface = surfaceColor,
        onSurface = onBackgroundColor
    )

    val baseDarkScheme = SpendSyncDarkColors.copy(
        primary = primaryColor,
        primaryContainer = primaryColor.copy(alpha = 0.25f),
        background = if (accentColorName == "Kakariki Green") Color(0xFF141A0F) else NeutralBlack,
        surface = if (accentColorName == "Kakariki Green") Color(0xFF1B2416) else NeutralDark,
        onBackground = if (accentColorName == "Kakariki Green") Color(0xFFE9EDDF) else NeutralOffWhite,
        onSurface = if (accentColorName == "Kakariki Green") Color(0xFFE9EDDF) else NeutralOffWhite
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> baseDarkScheme
        else      -> baseLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Safe cast — only proceed if the context is actually an Activity.
            // During NavHost transitions the context may be wrapped, so we
            // walk up the chain rather than force-casting.
            val activity = view.context as? Activity ?: return@SideEffect
            val window   = activity.window

            // On API 35+ (Android 15), window.statusBarColor is deprecated and
            // will throw. Use WindowInsetsControllerCompat exclusively instead.
            // The status bar is transparent (edge-to-edge); each screen paints
            // its own background colour behind it via statusBarsPadding().
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Make both bars fully transparent so no black/grey band appears
            // behind the floating bottom pill or the status bar.
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor     = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }

            WindowInsetsControllerCompat(window, view).apply {
                // Dark icons on light background; light icons on dark background
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
