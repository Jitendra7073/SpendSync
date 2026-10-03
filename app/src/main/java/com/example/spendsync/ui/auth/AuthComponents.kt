package com.example.spendsync.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.cascadeIn
import com.example.spendsync.ui.theme.incomeColor
import androidx.compose.material3.Text as GradientText

enum class AuthMode { Login, Register }

/** The pages that live inside the one shell. */
enum class AuthPage { Login, Register, Forgot }

/** Title and one line under it, at the top of every auth page. */
@Composable
fun AuthPageHeader(title: String, subtitle: String) {
    val scheme = MaterialTheme.colorScheme
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

/**
 * ONE shell for log in, sign up and forgot password. The backdrop, the hero and the panel are drawn once and
 * stay where they are; only the form inside the panel changes (a short slide + fade). Separate screens
 * made the whole design slide out and in on every switch, which looked like it was drawn twice.
 *
 * When the keyboard opens, or the phone is short, the hero shrinks so the form always has room.
 */
@Composable
fun AuthShell(
    page: AuthPage,
    onPageChange: (AuthPage) -> Unit,
    onBack: (() -> Unit)?,
    pageContent: @Composable androidx.compose.foundation.layout.ColumnScope.(AuthPage) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val tall = LocalConfiguration.current.screenHeightDp >= 740
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val panelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    Box(Modifier.fillMaxSize()) {
        AuthBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back), onClick = onBack)
            }
            AnimatedVisibility(
                visible = !keyboardOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AuthHero(small = !tall, extras = tall && page != AuthPage.Forgot)
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 2 } + fadeIn(),
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxSize()
                            .shadow(24.dp, panelShape, ambientColor = scheme.primary.copy(alpha = 0.2f), spotColor = scheme.primary.copy(alpha = 0.25f))
                            .clip(panelShape)
                            .background(scheme.surface.copy(alpha = 0.97f))
                            .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.7f), panelShape)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(top = 22.dp, bottom = 16.dp),
                    ) {
                        AnimatedVisibility(visible = page != AuthPage.Forgot, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column {
                                AuthModeSwitch(if (page == AuthPage.Register) AuthMode.Register else AuthMode.Login) {
                                    onPageChange(if (it == AuthMode.Register) AuthPage.Register else AuthPage.Login)
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                val dir = if (forward) 1 else -1
                                (fadeIn(tween(240, delayMillis = 80)) + slideInHorizontally(tween(320)) { it / 8 * dir }) togetherWith
                                    (fadeOut(tween(100)) + slideOutHorizontally(tween(320)) { -it / 8 * dir }) using SizeTransform(clip = false)
                            },
                            label = "auth_page",
                        ) { p ->
                            Column(Modifier.fillMaxWidth()) { pageContent(p) }
                        }
                    }
                }
            }
        }
    }
}

// ── Backdrop ─────────────────────────────────────────────────────────────────

/** Soft colour washes that drift slowly over a faint dot grid. Everything follows the app theme. */
@Composable
private fun AuthBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val drift = rememberInfiniteTransition(label = "auth_backdrop")
    val a by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val dot = scheme.onBackground.copy(alpha = 0.07f)

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(0f to scheme.primary.copy(alpha = 0.30f), 0.6f to Color.Transparent))
            fun blob(color: Color, center: Offset, radius: Float) =
                drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius), radius, center)
            blob(scheme.primary.copy(alpha = 0.40f), Offset(size.width * (0.05f + 0.5f * a), size.height * (0.06f + 0.10f * b)), size.width * 0.75f)
            blob(scheme.tertiary.copy(alpha = 0.30f), Offset(size.width * (0.95f - 0.5f * b), size.height * (0.30f - 0.08f * a)), size.width * 0.65f)
            blob(scheme.secondary.copy(alpha = 0.22f), Offset(size.width * (0.2f + 0.3f * b), size.height * (0.55f + 0.1f * a)), size.width * 0.6f)
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val step = 26.dp.toPx()
                val rows = (size.height * 0.55f / step).toInt()
                val cols = (size.width / step).toInt() + 1
                onDrawBehind {
                    for (r in 0..rows) for (c in 0..cols) drawCircle(dot, 1.1.dp.toPx(), Offset(c * step, r * step))
                }
            },
        )
    }
}

// ── Hero ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthHero(small: Boolean, extras: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
    val headline = if (small) 28.sp else 36.sp
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).cascadeIn(0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(if (small) 36.dp else 44.dp)
            Spacer(Modifier.width(10.dp))
            Text("SpendSync", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = scheme.onBackground)
        }
        Spacer(Modifier.height(if (small) 12.dp else 22.dp))
        GradientText(tr(R.string.auth_hero_line1), style = TextStyle(fontSize = headline, fontWeight = FontWeight.ExtraBold, lineHeight = headline * 1.1f, color = scheme.onBackground))
        GradientText(tr(R.string.auth_hero_line2), style = TextStyle(fontSize = headline, fontWeight = FontWeight.ExtraBold, lineHeight = headline * 1.1f, brush = gradient))
        Spacer(Modifier.height(8.dp))
        Text(tr(R.string.auth_hero_sub), fontSize = 15.sp, lineHeight = 21.sp, color = scheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp))
        AnimatedVisibility(visible = extras, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureChip(Icons.Default.Lock, tr(R.string.auth_chip_private))
                    FeatureChip(Icons.Default.AutoAwesome, tr(R.string.auth_chip_ai))
                    FeatureChip(Icons.Default.Sync, tr(R.string.auth_chip_sync))
                }
                Spacer(Modifier.height(18.dp))
                FloatingCards()
            }
        }
    }
}

@Composable
private fun BrandBadge(size: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    val glow = rememberInfiniteTransition(label = "badge")
    val pulse by glow.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulse")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 14.dp).graphicsLayer { alpha = 0.35f * pulse; scaleX = 0.9f + 0.15f * pulse; scaleY = 0.9f + 0.15f * pulse }.background(scheme.primary, CircleShape))
        Box(
            Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Wallet, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
private fun FeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(scheme.surface.copy(alpha = 0.7f))
            .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface)
    }
}

/** Three decorative cards that drift up and down. They only illustrate the app, so screen readers skip them. */
@Composable
private fun FloatingCards() {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val t = rememberInfiniteTransition(label = "cards")
    val bobA by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(3400, easing = EaseInOutSine), RepeatMode.Reverse), label = "a")
    val bobB by t.animateFloat(5f, -5f, infiniteRepeatable(tween(4100, easing = EaseInOutSine), RepeatMode.Reverse), label = "b")
    val bobC by t.animateFloat(-4f, 7f, infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "c")
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1300)) }
    val shape = RoundedCornerShape(18.dp)
    fun Modifier.card() = this
        .shadow(10.dp, shape, ambientColor = scheme.primary.copy(alpha = 0.15f), spotColor = scheme.primary.copy(alpha = 0.2f))
        .clip(shape)
        .background(scheme.surface.copy(alpha = 0.92f))
        .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.7f), shape)

    Box(Modifier.fillMaxWidth().height(138.dp).clearAndSetSemantics { }) {
        // Balance with a sparkline
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth(0.56f).graphicsLayer { translationY = bobA * density.density }.card().padding(12.dp),
        ) {
            Text(tr(R.string.auth_card_balance), fontSize = 11.sp, color = scheme.onSurfaceVariant)
            Text("₹48,250", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            val line = incomeColor()
            Canvas(Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp)) {
                val pts = listOf(0.7f, 0.55f, 0.62f, 0.4f, 0.48f, 0.28f, 0.34f, 0.12f)
                val path = Path()
                val fill = Path()
                pts.forEachIndexed { i, p ->
                    val x = size.width * i / (pts.size - 1)
                    val y = size.height * p
                    if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
                }
                fill.lineTo(size.width, size.height); fill.close()
                clipRect(right = size.width * grow.value) {
                    drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent)))
                    drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        // Budget ring
        Column(
            Modifier.align(Alignment.TopEnd).fillMaxWidth(0.38f).graphicsLayer { translationY = bobB * density.density }.card().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                val ring = scheme.primary
                val track = scheme.outlineVariant.copy(alpha = 0.6f)
                Canvas(Modifier.fillMaxSize()) {
                    val s = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 3.dp.toPx()
                    val box = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = s)
                    drawArc(ring, -90f, 360f * 0.72f * grow.value, false, Offset(inset, inset), box, style = s)
                }
                Text("72%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            }
            Text(tr(R.string.auth_card_budget), fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
        // Assistant bubble
        Row(
            Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.66f).graphicsLayer { translationY = bobC * density.density }.card().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(tr(R.string.auth_card_ai), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface)
        }
    }
}

// ── Log in / Sign up switch ──────────────────────────────────────────────────

@Composable
private fun AuthModeSwitch(mode: AuthMode, onChange: (AuthMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(scheme.surfaceVariant.copy(alpha = 0.6f)).padding(4.dp),
    ) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (mode == AuthMode.Login) 0.dp else half, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "mode_x")
        Box(Modifier.offset(x = x).width(half).fillMaxHeight().clip(CircleShape).background(scheme.primary))
        Row(Modifier.fillMaxSize()) {
            listOf(AuthMode.Login to R.string.log_in, AuthMode.Register to R.string.sign_up).forEach { (m, res) ->
                val selected = m == mode
                val color by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, tween(200), label = "mode_color")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).selectable(selected = selected, role = Role.Tab) { if (!selected) onChange(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr(res), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}

// ── Password strength + code boxes ───────────────────────────────────────────

/** Three-segment bar and a word under a new password, so people know when it is good enough. */
@Composable
fun PasswordStrength(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val level = passwordStrength(password)
    val color by animateColorAsState(
        when (level) { 1 -> scheme.error; 2 -> scheme.tertiary; else -> incomeColor() },
        tween(200), label = "strength_color",
    )
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i < level) color else scheme.outlineVariant.copy(alpha = 0.6f)),
                )
            }
        }
        Text(
            tr(when (level) { 1 -> R.string.auth_strength_weak; 2 -> R.string.auth_strength_ok; else -> R.string.auth_strength_strong }) +
                if (level == 1) " · " + tr(R.string.auth_password_hint) else "",
            fontSize = 12.sp, color = if (level == 1) scheme.error else scheme.onSurfaceVariant,
        )
    }
}

/**
 * Six boxes for the emailed code. One hidden text field underneath takes the typing (and a pasted code),
 * letters switch to capitals by themselves, and the next empty box is highlighted.
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val label = tr(R.string.auth_code_label)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onDone() }),
        modifier = modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(RESET_CODE_LENGTH) { i ->
                    val char = value.getOrNull(i)?.toString().orEmpty()
                    val active = focused && i == value.length.coerceAtMost(RESET_CODE_LENGTH - 1)
                    val border by animateColorAsState(
                        when { active -> scheme.primary; char.isNotEmpty() -> scheme.primary.copy(alpha = 0.5f); else -> scheme.outline },
                        tween(150), label = "code_border",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (char.isNotEmpty()) scheme.primary.copy(alpha = 0.08f) else scheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = scheme.onSurface, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}
