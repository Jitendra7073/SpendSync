package com.example.spendsync.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.cascadeIn
import com.example.spendsync.ui.theme.incomeColor

/**
 * The one layout for log in, sign up and forgot password: the same animated backdrop and frosted card as
 * Settings, the brand at the top, an optional back button, and a footer for the "other way in" links.
 * The page scrolls and lifts with the keyboard, so a field is never hidden behind it.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SettingsBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back), onClick = onBack)
            }
            Spacer(Modifier.height(4.dp))
            BrandMark(Modifier.cascadeIn(0))
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().cascadeIn(1).glassCard(24).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(22.dp))
                content()
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().cascadeIn(2),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = footer,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Wallet, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("SpendSync", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = scheme.onBackground)
    }
}

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
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (char.isNotEmpty()) scheme.primary.copy(alpha = 0.07f) else Color.Transparent)
                            .border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = scheme.onSurface, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}
