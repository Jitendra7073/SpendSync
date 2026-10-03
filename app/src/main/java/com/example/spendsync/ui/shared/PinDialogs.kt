package com.example.spendsync.ui.shared

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.DialogTone
import com.example.spendsync.ui.components.Text
import com.example.spendsync.utils.generateSalt
import com.example.spendsync.utils.hashPin
import com.example.spendsync.utils.verifyPin
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

/**
 * Four dots that fill as you type. A hidden number field underneath does the real input (so the
 * numeric keyboard, paste protection and screen-reader support all work), and the whole row is
 * a tap target that brings the keyboard back. A wrong PIN shakes the dots and shows [error].
 */
@Composable
private fun PinEntry(value: String, onValueChange: (String) -> Unit, error: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(error) {
        if (error != null) {
            repeat(3) {
                shake.animateTo(10f, tween(40))
                shake.animateTo(-10f, tween(40))
            }
            shake.animateTo(0f, tween(40))
        }
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = value,
            onValueChange = { v -> if (v.length <= PIN_LENGTH && v.all(Char::isDigit)) onValueChange(v) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus).semantics { contentDescription = tr(R.string.pin_1_of_2_digits_entered, value.length, PIN_LENGTH) },
        )
        Row(
            Modifier
                .offset(x = shake.value.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    focus.requestFocus()
                    keyboard?.show()
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(PIN_LENGTH) { i ->
                val filled = i < value.length
                val scale by animateFloatAsState(if (filled) 1f else 0.0f, spring(dampingRatio = 0.5f), label = "pin_dot")
                val ring = if (error != null) scheme.error else if (i == value.length) scheme.primary else scheme.outline
                Box(
                    Modifier.size(20.dp).clip(CircleShape).border(2.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .background(if (error != null) scheme.error else scheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
fun PinUnlockDialog(
    sessionDataStore: SessionDataStore,
    onUnlock: (durationSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
    onNeedsSetup: () -> Unit = {},
) {
    val pinHash by sessionDataStore.pinHash.collectAsState(initial = null)
    val pinSalt by sessionDataStore.pinSalt.collectAsState(initial = null)
    val durationSeconds by sessionDataStore.amountVisibilityDurationSeconds.collectAsState(initial = 60)

    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Sign-out clears the PIN but leaves masking on — there's nothing to check against,
    // so offer setup instead of a dialog that can never succeed.
    val noPinSet = pinHash == null && pinSalt == null

    fun tryUnlock(pin: String) {
        val hash = pinHash
        val salt = pinSalt
        if (hash != null && salt != null && verifyPin(pin, salt, hash)) {
            onUnlock(durationSeconds)
        } else {
            error = tr(R.string.that_pin_isn_t_right_try)
            entered = ""
        }
    }

    if (noPinSet) {
        AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.create_a_pin_first),
            message = tr(R.string.your_amounts_are_hidden_but_no),
            icon = Icons.Default.Lock,
            primary = DialogAction(tr(R.string.set_up_pin), onNeedsSetup),
            secondary = DialogAction(tr(R.string.cancel), onDismiss),
        )
    } else {
        AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.enter_your_pin),
            message = tr(R.string.amounts_will_show_for_a_short),
            icon = Icons.Default.LockOpen,
            primary = DialogAction(tr(R.string.show_amounts), { tryUnlock(entered) }, enabled = entered.length == PIN_LENGTH),
            secondary = DialogAction(tr(R.string.cancel), onDismiss),
        ) {
            PinEntry(entered, { entered = it; error = null; if (it.length == PIN_LENGTH) tryUnlock(it) }, error)
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private enum class PinSetupStep { CURRENT, NEW, CONFIRM }

@Composable
fun PinSetupDialog(
    sessionDataStore: SessionDataStore,
    requireCurrentPin: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pinHash by sessionDataStore.pinHash.collectAsState(initial = null)
    val pinSalt by sessionDataStore.pinSalt.collectAsState(initial = null)

    var step by remember { mutableStateOf(if (requireCurrentPin) PinSetupStep.CURRENT else PinSetupStep.NEW) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (step) {
        PinSetupStep.CURRENT -> tr(R.string.enter_your_current_pin)
        PinSetupStep.NEW -> tr(R.string.choose_a_new_pin)
        PinSetupStep.CONFIRM -> tr(R.string.type_it_once_more)
    }
    val message = when (step) {
        PinSetupStep.CURRENT -> tr(R.string.we_need_to_check_it_s)
        PinSetupStep.NEW -> tr(R.string.pick_4_numbers_you_ll_remember)
        PinSetupStep.CONFIRM -> tr(R.string.enter_the_same_4_numbers_to)
    }
    val pinValue = when (step) {
        PinSetupStep.CURRENT -> currentPin
        PinSetupStep.NEW -> newPin
        PinSetupStep.CONFIRM -> confirmPin
    }

    fun advance() {
        when (step) {
            PinSetupStep.CURRENT -> {
                val hash = pinHash
                val salt = pinSalt
                if (hash != null && salt != null && verifyPin(currentPin, salt, hash)) step = PinSetupStep.NEW
                else { error = tr(R.string.that_pin_isn_t_right_try); currentPin = "" }
            }
            PinSetupStep.NEW -> step = PinSetupStep.CONFIRM
            PinSetupStep.CONFIRM -> {
                if (confirmPin != newPin) {
                    error = tr(R.string.the_pins_don_t_match_try)
                    confirmPin = ""
                } else {
                    scope.launch {
                        val salt = generateSalt()
                        sessionDataStore.setPin(hashPin(newPin, salt), salt)
                        onDone()
                    }
                }
            }
        }
    }

    AppDialog(
        onDismiss = onDismiss,
        title = title,
        message = message,
        icon = Icons.Default.Lock,
        tone = if (error != null) DialogTone.Danger else DialogTone.Info,
        primary = DialogAction(if (step == PinSetupStep.CONFIRM) tr(R.string.save_pin) else tr(R.string.next), { advance() }, enabled = pinValue.length == PIN_LENGTH),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        // Re-keyed per step so the dots and keyboard reset cleanly each time.
        androidx.compose.runtime.key(step) {
            PinEntry(
                value = pinValue,
                onValueChange = { v ->
                    when (step) {
                        PinSetupStep.CURRENT -> currentPin = v
                        PinSetupStep.NEW -> newPin = v
                        PinSetupStep.CONFIRM -> confirmPin = v
                    }
                    error = null
                },
                error = error,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
