package com.example.spendsync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── SpendSync Brand ─────────────────────────────────────────────────────────
val BrandBlue        = Color(0xFF1A56DB)   // primary blue (splash / header)
val BrandBlueDark    = Color(0xFF1040B0)   // pressed / dark variant
val BrandBlueSurface = Color(0xFF1D4ED8)   // slightly lighter surface
val BrandYellow      = Color(0xFFFBBD08)   // accent yellow (underline bar)
val BrandYellowDark  = Color(0xFFD97706)   // yellow pressed

// ── Neutrals ────────────────────────────────────────────────────────────────
val NeutralWhite     = Color(0xFFFFFFFF)
val NeutralOffWhite  = Color(0xFFF9FAFB)
val NeutralLight     = Color(0xFFE5E7EB)
val NeutralMid       = Color(0xFF9CA3AF)
val NeutralDark      = Color(0xFF374151)
val NeutralBlack     = Color(0xFF111827)

// ── Semantic ─────────────────────────────────────────────────────────────────
val SemanticError    = Color(0xFFDC2626)
val SemanticSuccess  = Color(0xFF16A34A)
val SemanticWarning  = Color(0xFFF59E0B)
val SemanticInfo     = Color(0xFF2563EB)

// ── Kakariki UI ──────────────────────────────────────────────────────────────
val KakarikiBg       = Color(0xFFE9EDDF) // light beige/green background
val KakarikiActive   = Color(0xFFC7D3A6) // active olive pill
val KakarikiOnBg     = Color(0xFF2E3324) // dark olive/black text

// ── Chart categorical palette ─────────────────────────────────────────────────
// Fixed, CVD-validated order — never reorder or cycle (verified with the
// dataviz skill's validator against both surfaces). Slot 1 matches the app's
// own brand blue so charts read as "SpendSync's blue", not a generic default.
// Categories beyond slot 7 fold into "Other" (NeutralMid, not a real identity).
val ChartCategoricalLight = listOf(
    BrandBlue,
    Color(0xFF1BAF7A),
    Color(0xFFEDA100),
    Color(0xFF008300),
    Color(0xFF4A3AA7),
    Color(0xFFE34948),
    Color(0xFFE87BA4),
    Color(0xFFEB6834),
)

// Same 8 identities, re-stepped for the dark surface (not a flip of the light
// values — validated separately per the skill's dark-mode rule).
val ChartCategoricalDark = listOf(
    BrandBlueSurface,
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFF008300),
    Color(0xFF9085E9),
    Color(0xFFE66767),
    Color(0xFFD55181),
    Color(0xFFD95926),
)

@Composable
fun chartCategoricalColors(): List<Color> =
    if (isSystemInDarkTheme()) ChartCategoricalDark else ChartCategoricalLight

// ── Legacy / Material fallbacks ──────────────────────────────────────────────
val Purple80         = Color(0xFFD0BCFF)
val PurpleGrey80     = Color(0xFFCCC2DC)
val Pink80           = Color(0xFFEFB8C8)
val Purple40         = Color(0xFF6650a4)
val PurpleGrey40     = Color(0xFF625b71)
val Pink40           = Color(0xFF7D5260)
