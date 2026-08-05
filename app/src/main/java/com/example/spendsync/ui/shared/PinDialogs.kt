package com.example.spendsync.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.utils.generateSalt
import com.example.spendsync.utils.hashPin
import com.example.spendsync.utils.verifyPin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinUnlockDialog(
    sessionDataStore: SessionDataStore,
    onUnlock: (durationSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pinHash by sessionDataStore.pinHash.collectAsState(initial = null)
    val pinSalt by sessionDataStore.pinSalt.collectAsState(initial = null)
    val durationSeconds by sessionDataStore.amountVisibilityDurationSeconds.collectAsState(initial = 60)

    var enteredPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Enter PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { enteredPin = it; error = null } },
                    label = { Text("4-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = enteredPin.length == 4,
                        onClick = {
                            val hash = pinHash
                            val salt = pinSalt
                            if (hash != null && salt != null && verifyPin(enteredPin, salt, hash)) {
                                onUnlock(durationSeconds)
                            } else {
                                error = "Incorrect PIN"
                                enteredPin = ""
                            }
                        },
                    ) { Text("Unlock") }
                }
            }
        }
    }
}

private enum class PinSetupStep { CURRENT, NEW, CONFIRM }

@OptIn(ExperimentalMaterial3Api::class)
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
        PinSetupStep.CURRENT -> "Enter current PIN"
        PinSetupStep.NEW -> "Choose a new PIN"
        PinSetupStep.CONFIRM -> "Confirm new PIN"
    }
    val pinValue = when (step) {
        PinSetupStep.CURRENT -> currentPin
        PinSetupStep.NEW -> newPin
        PinSetupStep.CONFIRM -> confirmPin
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { v ->
                        if (v.length <= 4 && v.all(Char::isDigit)) {
                            when (step) {
                                PinSetupStep.CURRENT -> currentPin = v
                                PinSetupStep.NEW -> newPin = v
                                PinSetupStep.CONFIRM -> confirmPin = v
                            }
                            error = null
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = pinValue.length == 4,
                        onClick = {
                            when (step) {
                                PinSetupStep.CURRENT -> {
                                    val hash = pinHash
                                    val salt = pinSalt
                                    if (hash != null && salt != null && verifyPin(currentPin, salt, hash)) {
                                        step = PinSetupStep.NEW
                                    } else {
                                        error = "Incorrect PIN"
                                        currentPin = ""
                                    }
                                }
                                PinSetupStep.NEW -> {
                                    step = PinSetupStep.CONFIRM
                                }
                                PinSetupStep.CONFIRM -> {
                                    if (confirmPin != newPin) {
                                        error = "PINs don't match"
                                        confirmPin = ""
                                    } else {
                                        scope.launch {
                                            val salt = generateSalt()
                                            val hash = hashPin(newPin, salt)
                                            sessionDataStore.setPin(hash, salt)
                                            onDone()
                                        }
                                    }
                                }
                            }
                        },
                    ) { Text(if (step == PinSetupStep.CONFIRM) "Save" else "Next") }
                }
            }
        }
    }
}
