# Amount Masking / PIN Privacy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mask every displayed amount over ₹1000 behind `★★★★★` + a tap-to-reveal eye icon, gated by a locally-stored PIN, with one shared app-wide unlock session (configurable duration) rather than per-amount unlocking.

**Architecture:** Fully client-side, no backend changes. A new `AmountVisibilityState` (shared, instantiated once in `MainScreen.kt`, same pattern as the existing `DateFilterState`) tracks whether the current unlock session is active. A new `MaskableAmountText` composable is the single drop-in replacement for every existing `Text(text = formatInr(...))` call site — it renders the real amount when unmasked, or masked stars + an eye icon (tap → opens the shared PIN dialog) when masked. The PIN itself is never sent to the backend; it's stored locally as a salted SHA-256 hash.

**Tech Stack:** Kotlin, Jetpack Compose, `java.security.MessageDigest`/`SecureRandom` (stdlib, no new dependency), existing `SessionDataStore` (DataStore Preferences).

Spec: `docs/superpowers/specs/2026-08-05-amount-masking-design.md`

## Global Constraints

- Masking threshold: amount **strictly greater than** ₹1000 is masked (₹1000.00 itself is NOT masked). Applies independently to every individually-displayed amount, not to totals-of-totals.
- Opt-in only — `amountMaskingEnabled` defaults to `false`. Until enabled in Settings, nothing changes anywhere.
- PIN is 4-digit numeric, never sent to the backend, stored only as `sha256(salt + pin)` with a random per-PIN salt (both in `SessionDataStore`). Never store or log the raw PIN.
- Changing the PIN requires the current PIN first (verified), then the new PIN entered twice.
- One shared unlock session app-wide: unlocking via any eye icon anywhere reveals every masked amount everywhere, for a duration configured in Settings (30s / 1min / 5min / 15min, default 60s). Session is in-memory only (an `AmountVisibilityState` instance living in `MainScreen.kt`) — it does not persist across app restart, and does not need to.
- `AddExpenseScreen.kt` has no `formatInr` call sites today — nothing to retrofit there, confirmed by direct grep. Do not add masking to a screen that doesn't display amounts.
- `HoldReminderNotifier.kt`'s system notification text amount is explicitly OUT OF SCOPE — a system notification isn't a Compose screen the in-app PIN session can gate the same way. Leave it unmasked.
- On sign-out (`SessionDataStore.clearSession()`), the stored PIN hash+salt must be cleared (it's account-scoped secret material) — but the `amountMaskingEnabled`/`amountVisibilityDurationSeconds` preferences should NOT be cleared, matching how other display preferences (dark mode, date format) already survive sign-out. If the toggle is left on with no PIN, the next unlock attempt should fall through to PIN setup rather than crash — see Task 2.
- Follow existing code style: no KDoc on obvious code, comments only for non-obvious "why".

---

### Task 1: Foundation — `SessionDataStore` additions + pure functions (TDD)

**Files:**
- Create: `app/src/main/java/com/example/spendsync/utils/PinHashing.kt`
- Test: `app/src/test/java/com/example/spendsync/utils/PinHashingTest.kt`
- Create: `app/src/main/java/com/example/spendsync/utils/AmountMasking.kt`
- Test: `app/src/test/java/com/example/spendsync/utils/AmountMaskingTest.kt`
- Modify: `app/src/main/java/com/example/spendsync/data/local/SessionDataStore.kt`

**Interfaces:**
- Produces: `internal fun generateSalt(): String`, `internal fun hashPin(pin: String, salt: String): String`, `internal fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean`, `internal const val AMOUNT_MASK_THRESHOLD = 1000.0`, `internal fun shouldMaskAmount(amount: Double, isVisible: Boolean): Boolean`. Also on `SessionDataStore`: `val amountMaskingEnabled: Flow<Boolean>` (default `false`), `val amountVisibilityDurationSeconds: Flow<Int>` (default `60`), `val pinHash: Flow<String?>`, `val pinSalt: Flow<String?>`, `suspend fun updateAmountMaskingEnabled(enabled: Boolean)`, `suspend fun updateAmountVisibilityDurationSeconds(seconds: Int)`, `suspend fun setPin(hash: String, salt: String)`. All of this is consumed by Task 2 onward.

- [ ] **Step 1: Write the failing tests for PIN hashing**

```kotlin
// app/src/test/java/com/example/spendsync/utils/PinHashingTest.kt
package com.example.spendsync.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashingTest {

    @Test
    fun `hashPin is deterministic for the same pin and salt`() {
        assertEquals(hashPin("1234", "abc"), hashPin("1234", "abc"))
    }

    @Test
    fun `different pins produce different hashes for the same salt`() {
        assertNotEquals(hashPin("1234", "abc"), hashPin("5678", "abc"))
    }

    @Test
    fun `different salts produce different hashes for the same pin`() {
        assertNotEquals(hashPin("1234", "abc"), hashPin("1234", "xyz"))
    }

    @Test
    fun `verifyPin returns true for the correct pin`() {
        val salt = generateSalt()
        val hash = hashPin("4321", salt)
        assertTrue(verifyPin("4321", salt, hash))
    }

    @Test
    fun `verifyPin returns false for the wrong pin`() {
        val salt = generateSalt()
        val hash = hashPin("4321", salt)
        assertTrue(!verifyPin("0000", salt, hash))
    }

    @Test
    fun `generateSalt produces different non-empty values each call`() {
        val a = generateSalt()
        val b = generateSalt()
        assertTrue(a.isNotEmpty())
        assertTrue(b.isNotEmpty())
        assertNotEquals(a, b)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.utils.PinHashingTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write `PinHashing.kt`**

```kotlin
package com.example.spendsync.utils

import java.security.MessageDigest
import java.security.SecureRandom

internal fun generateSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun hashPin(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest((salt + pin).toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

internal fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean =
    hashPin(pin, salt) == expectedHash
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.utils.PinHashingTest"`
Expected: PASS (all 6 tests)

- [ ] **Step 5: Write the failing tests for the masking threshold**

```kotlin
// app/src/test/java/com/example/spendsync/utils/AmountMaskingTest.kt
package com.example.spendsync.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountMaskingTest {

    @Test
    fun `amount above threshold and not visible is masked`() {
        assertTrue(shouldMaskAmount(1500.0, isVisible = false))
    }

    @Test
    fun `amount above threshold and visible is not masked`() {
        assertFalse(shouldMaskAmount(1500.0, isVisible = true))
    }

    @Test
    fun `amount exactly at threshold is not masked`() {
        assertFalse(shouldMaskAmount(1000.0, isVisible = false))
    }

    @Test
    fun `amount below threshold is never masked regardless of visibility`() {
        assertFalse(shouldMaskAmount(999.0, isVisible = false))
        assertFalse(shouldMaskAmount(999.0, isVisible = true))
    }
}
```

- [ ] **Step 6: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.utils.AmountMaskingTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 7: Write `AmountMasking.kt`**

```kotlin
package com.example.spendsync.utils

internal const val AMOUNT_MASK_THRESHOLD = 1000.0

internal fun shouldMaskAmount(amount: Double, isVisible: Boolean): Boolean =
    amount > AMOUNT_MASK_THRESHOLD && !isVisible
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.utils.AmountMaskingTest"`
Expected: PASS (all 4 tests)

- [ ] **Step 9: Add the `SessionDataStore` preferences**

Open `SessionDataStore.kt` and find the `companion object`'s key declarations (the block with `KEY_DARK_MODE`, `KEY_AUTO_CAPTURE_ENABLED`, `KEY_AUTO_CAPTURE_PACKAGES`, etc.). Add, following the exact same declaration style:

```kotlin
        private val KEY_AMOUNT_MASKING_ENABLED = booleanPreferencesKey("amount_masking_enabled")
        private val KEY_AMOUNT_VISIBILITY_DURATION_SECONDS = intPreferencesKey("amount_visibility_duration_seconds")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
```

This is the first `intPreferencesKey` in this file — add the import `androidx.datastore.preferences.core.intPreferencesKey` alongside the existing `booleanPreferencesKey`/`stringPreferencesKey` imports at the top of the file.

Find the flow declarations section (alongside `autoCaptureEnabled`/`autoCapturePackages`) and add:

```kotlin
    val amountMaskingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AMOUNT_MASKING_ENABLED] ?: false
    }

    val amountVisibilityDurationSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AMOUNT_VISIBILITY_DURATION_SECONDS] ?: 60
    }

    val pinHash: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_HASH]
    }

    val pinSalt: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_SALT]
    }
```

Find the write-functions section (alongside `updateAutoCaptureEnabled`/`updateAutoCapturePackages`) and add:

```kotlin
    suspend fun updateAmountMaskingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AMOUNT_MASKING_ENABLED] = enabled }
    }

    suspend fun updateAmountVisibilityDurationSeconds(seconds: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_AMOUNT_VISIBILITY_DURATION_SECONDS] = seconds }
    }

    suspend fun setPin(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_SALT] = salt
        }
    }
```

- [ ] **Step 10: Clear the PIN on sign-out**

Find `clearSession()` and add `KEY_PIN_HASH`/`KEY_PIN_SALT` to its removal list, alongside the existing `prefs.remove(KEY_AUTO_CAPTURE_PACKAGES)`-style lines:

```kotlin
            prefs.remove(KEY_PIN_HASH)
            prefs.remove(KEY_PIN_SALT)
```

Do NOT add `KEY_AMOUNT_MASKING_ENABLED`/`KEY_AMOUNT_VISIBILITY_DURATION_SECONDS` to `clearSession()` or `clearLocalData()` — per Global Constraints, these are display preferences that survive sign-out like dark mode does.

- [ ] **Step 11: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/example/spendsync/utils/PinHashing.kt app/src/test/java/com/example/spendsync/utils/PinHashingTest.kt app/src/main/java/com/example/spendsync/utils/AmountMasking.kt app/src/test/java/com/example/spendsync/utils/AmountMaskingTest.kt app/src/main/java/com/example/spendsync/data/local/SessionDataStore.kt
git commit -m "feat: add PIN hashing, amount-masking threshold, and their local storage"
```

---

### Task 2: Shared UI pieces — `AmountVisibilityState`, `MaskableAmountText`, PIN dialogs

**Files:**
- Create: `app/src/main/java/com/example/spendsync/ui/shared/AmountVisibilityState.kt`
- Create: `app/src/main/java/com/example/spendsync/ui/shared/MaskableAmountText.kt`
- Create: `app/src/main/java/com/example/spendsync/ui/shared/PinDialogs.kt`

**Interfaces:**
- Consumes: `generateSalt`/`hashPin`/`verifyPin`/`shouldMaskAmount` (Task 1), `formatInr` (existing), `SessionDataStore` (existing, Task 1's new members).
- Produces: `class AmountVisibilityState { val isVisible: Boolean; val unlockedUntil: Instant?; val showUnlockPrompt: Boolean; fun requestUnlock(); fun dismissUnlockPrompt(); fun unlock(durationSeconds: Int); fun lock() }`, `@Composable fun MaskableAmountText(amount: Double, visibility: AmountVisibilityState, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, prefix: String = "", textAlign: TextAlign? = null)`, `@Composable fun PinUnlockDialog(sessionDataStore: SessionDataStore, onUnlock: (durationSeconds: Int) -> Unit, onDismiss: () -> Unit)`, `@Composable fun PinSetupDialog(sessionDataStore: SessionDataStore, requireCurrentPin: Boolean, onDone: () -> Unit, onDismiss: () -> Unit)`. Tasks 3-6 consume all of these directly.

- [ ] **Step 1: Write `AmountVisibilityState.kt`**

```kotlin
package com.example.spendsync.ui.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant

/**
 * App-wide "amount visibility" session — one PIN unlock reveals every
 * masked amount everywhere for a configured duration, then everything
 * re-masks automatically. In-memory only; deliberately does not survive
 * app restart (reopening the app always starts re-masked).
 */
class AmountVisibilityState {
    var isVisible by mutableStateOf(false)
        private set
    var unlockedUntil: Instant? = null
        private set
    var showUnlockPrompt by mutableStateOf(false)
        private set

    fun requestUnlock() {
        if (!isVisible) showUnlockPrompt = true
    }

    fun dismissUnlockPrompt() {
        showUnlockPrompt = false
    }

    fun unlock(durationSeconds: Int) {
        isVisible = true
        unlockedUntil = Instant.now().plusSeconds(durationSeconds.toLong())
        showUnlockPrompt = false
    }

    fun lock() {
        isVisible = false
        unlockedUntil = null
    }
}
```

- [ ] **Step 2: Write `MaskableAmountText.kt`**

```kotlin
package com.example.spendsync.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.spendsync.utils.formatInr
import com.example.spendsync.utils.shouldMaskAmount

/**
 * Drop-in replacement for `Text(text = formatInr(amount), ...)` — renders
 * the real amount when unmasked, or "★★★★★" plus a tap-to-reveal eye icon
 * when [amount] exceeds the masking threshold and [visibility] isn't
 * currently unlocked. [prefix] carries a "+"/"-" sign some call sites
 * already prepend, so it stays part of the masked/unmasked text either way.
 */
@Composable
fun MaskableAmountText(
    amount: Double,
    visibility: AmountVisibilityState,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    prefix: String = "",
    textAlign: TextAlign? = null,
) {
    if (shouldMaskAmount(amount, visibility.isVisible)) {
        Row(
            modifier = modifier.clickable { visibility.requestUnlock() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$prefix★★★★★",
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Show amount",
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
    } else {
        Text(
            text = "$prefix${formatInr(amount)}",
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            modifier = modifier,
        )
    }
}
```

This needs `import androidx.compose.foundation.layout.size` for `Modifier.size(14.dp)` — add it alongside the other `androidx.compose.foundation.layout.*` imports.

- [ ] **Step 3: Write `PinDialogs.kt`**

```kotlin
package com.example.spendsync.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
                Spacer2()
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
                    Spacer2()
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer2()
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
                Spacer2()
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
                    Spacer2()
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer2()
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

@Composable
private fun Spacer2() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
}
```

The `Spacer2` helper at the bottom is a placeholder for consistent vertical spacing — if this reads awkwardly once you see it compiled, replace its three call sites with direct `Spacer(Modifier.height(12.dp))` calls (needs `import androidx.compose.foundation.layout.Spacer` and `androidx.compose.foundation.layout.height` instead) — either is fine, pick whichever compiles cleanest; the exact spacing value doesn't matter.

- [ ] **Step 4: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/shared/AmountVisibilityState.kt app/src/main/java/com/example/spendsync/ui/shared/MaskableAmountText.kt app/src/main/java/com/example/spendsync/ui/shared/PinDialogs.kt
git commit -m "feat: add amount visibility session, maskable amount text, and PIN dialogs"
```

---

### Task 3: HomeScreen retrofit

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `MaskableAmountText`/`AmountVisibilityState` (Task 2).
- Produces: `HomeScreen(..., amountVisibility: AmountVisibilityState, ...)` — a new required parameter. Task 6 (MainScreen wiring) passes it in; until then, `MainScreen.kt`'s existing call site won't compile — that's expected and resolved by Task 6, not this task.

Before editing, re-read the current file — it's been touched several times this session, confirm each site below still matches before changing it.

- [ ] **Step 1: Add the parameter**

Add `amountVisibility: AmountVisibilityState,` to `HomeScreen`'s parameter list (alongside `onOpenHolds`), and `import com.example.spendsync.ui.shared.AmountVisibilityState` and `import com.example.spendsync.ui.shared.MaskableAmountText`.

- [ ] **Step 2: Balance card headline**

Find:
```kotlin
                        Text(
                            text       = formatInr(allTimeBalance ?: 0.0),
                            color      = NeutralWhite,
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
```
Replace with:
```kotlin
                        MaskableAmountText(
                            amount     = allTimeBalance ?: 0.0,
                            visibility = amountVisibility,
                            color      = NeutralWhite,
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
```

- [ ] **Step 3: Balance card sub-stats (Total Balance and Hold Money)**

Find:
```kotlin
                                Text(
                                    text = formatInr((allTimeBalance ?: 0.0) + holdMoney),
                                    color = NeutralWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
```
Replace with:
```kotlin
                                MaskableAmountText(
                                    amount = (allTimeBalance ?: 0.0) + holdMoney,
                                    visibility = amountVisibility,
                                    color = NeutralWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
```

Find:
```kotlin
                                Text(
                                    text = formatInr(holdMoney),
                                    color = NeutralWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
```
Replace with:
```kotlin
                                MaskableAmountText(
                                    amount = holdMoney,
                                    visibility = amountVisibility,
                                    color = NeutralWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
```

- [ ] **Step 4: `SummaryTile` helper — change from a formatted-string param to a raw amount**

Find `SummaryTile`'s declaration (parameter `amount: String`) and its internal `Text(text = amount, ...)` (or however it renders the amount internally — read the actual body). Change the parameter from `amount: String` to `amount: Double`, add an `amountVisibility: AmountVisibilityState` parameter, and change the internal amount `Text(...)` to `MaskableAmountText(amount = amount, visibility = amountVisibility, ...)`, preserving whatever `color`/`fontSize`/`fontWeight` it already passes to that `Text`.

Then update both call sites — find:
```kotlin
            SummaryTile(
                label = LocalizationUtils.getTranslation("income", language),
                amount = formatInr(totalIncome),
```
change to:
```kotlin
            SummaryTile(
                label = LocalizationUtils.getTranslation("income", language),
                amount = totalIncome,
                amountVisibility = amountVisibility,
```
(keep the rest of that call — `icon`/`cardColor`/`borderColor`/`accentColor`/`isSelected`/`onClick`/`modifier` — unchanged) and similarly for the expenses tile:
```kotlin
            SummaryTile(
                label = LocalizationUtils.getTranslation("expenses", language),
                amount = formatInr(totalExpenses),
```
change to:
```kotlin
            SummaryTile(
                label = LocalizationUtils.getTranslation("expenses", language),
                amount = totalExpenses,
                amountVisibility = amountVisibility,
```

- [ ] **Step 5: Credit/debit totals with sign prefixes**

Find:
```kotlin
                Text(
                    text = "+${formatInr(creditTotal)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A),
                )
```
Replace with:
```kotlin
                MaskableAmountText(
                    amount = creditTotal,
                    visibility = amountVisibility,
                    prefix = "+",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A),
                )
```
And find:
```kotlin
                Text(
                    text = "-${formatInr(debitTotal)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                )
```
Replace with:
```kotlin
                MaskableAmountText(
                    amount = debitTotal,
                    visibility = amountVisibility,
                    prefix = "-",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                )
```

- [ ] **Step 6: Transaction row amount**

Find:
```kotlin
    val amountText = if (isCredit) {
        "+ ${formatInr(amountVal)}"
    } else {
        "- ${formatInr(amountVal)}"
    }
    val amountColor = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626)

    Text(
        text = amountText,
        color = amountColor,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
```
Replace with:
```kotlin
    val amountColor = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626)

    MaskableAmountText(
        amount = amountVal,
        visibility = amountVisibility,
        prefix = if (isCredit) "+ " else "- ",
        color = amountColor,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    )
```
(This composable — the one containing this row — needs `amountVisibility: AmountVisibilityState` added to ITS OWN parameter list too, and its call site further up in the file updated to pass `amountVisibility = amountVisibility`. Find where this composable is invoked and thread the parameter through.)

- [ ] **Step 7: Delete-confirmation dialog sentence**

Find:
```kotlin
                Text(
                    text = "${transaction.merchant.ifBlank { transaction.category }} — ${formatInr(amountVal)}",
                    fontSize = 12.sp,
                    color = NeutralMid,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
```
Replace with:
```kotlin
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${transaction.merchant.ifBlank { transaction.category }} — ",
                        fontSize = 12.sp,
                        color = NeutralMid,
                    )
                    MaskableAmountText(
                        amount = amountVal,
                        visibility = amountVisibility,
                        fontSize = 12.sp,
                        color = NeutralMid,
                    )
                }
```
This composable also needs `amountVisibility: AmountVisibilityState` added to its own parameter list and threaded from its call site, same as Step 6.

- [ ] **Step 8: Transaction detail dialog's Amount row**

Find:
```kotlin
InfoRow("Amount", "${if (isCredit) "+" else "-"} ${formatInr(amountVal)}")
```
First read `InfoRow`'s actual definition in this file to see its exact label/value styling (color, fontSize, spacing, `Row`/`Column` structure). Then replace this call with an inline `Row` that visually matches `InfoRow`'s existing label/value layout, but renders the value half via `MaskableAmountText` instead of a plain string — e.g. (adjust styling to match what you find `InfoRow` actually does):
```kotlin
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text("Amount", color = NeutralMid, fontSize = 13.sp)
    MaskableAmountText(
        amount = amountVal,
        visibility = amountVisibility,
        prefix = if (isCredit) "+ " else "- ",
        color = NeutralBlack,
        fontSize = 13.sp,
    )
}
```
This composable also needs `amountVisibility: AmountVisibilityState` added and threaded, same as Steps 6-7. Every other `InfoRow(...)` call in the same dialog (Category, Date, etc.) stays exactly as-is — only the Amount row changes.

- [ ] **Step 9: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: this will FAIL because `MainScreen.kt`'s call site doesn't pass `amountVisibility` yet — that's Task 6's job. Confirm the ONLY compile errors are about the missing `amountVisibility` argument at `HomeScreen`'s call site in `MainScreen.kt` (an unresolved/missing-parameter error there), not anything inside `HomeScreen.kt` itself. If there are errors inside `HomeScreen.kt`, fix those before reporting — those would be real bugs in this task's work.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt
git commit -m "feat: mask amounts on Home behind the shared visibility session"
```

---

### Task 4: PlaceholderScreens (Analytics + Budget) retrofit

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/placeholder/PlaceholderScreens.kt`

**Interfaces:**
- Consumes: `MaskableAmountText`/`AmountVisibilityState` (Task 2).
- Produces: `AnalyticsScreen(..., amountVisibility: AmountVisibilityState, ...)` and `BudgetScreen(..., amountVisibility: AmountVisibilityState, ...)` — new required parameters. Task 6 wires them; `MainScreen.kt`'s current calls to these two won't compile until then, same expected-failure situation as Task 3.

Before editing, re-read the current file to confirm each site below still matches.

- [ ] **Step 1: Add the parameter to both screens**

Add `amountVisibility: AmountVisibilityState,` to both `AnalyticsScreen`'s and `BudgetScreen`'s parameter lists, and `import com.example.spendsync.ui.shared.AmountVisibilityState` / `import com.example.spendsync.ui.shared.MaskableAmountText` at the top of the file.

- [ ] **Step 2: `OverviewStat` helper — change from formatted-string to raw amount**

Find `OverviewStat`'s declaration (parameter `amount: String`) and change it to `amount: Double` plus a new `amountVisibility: AmountVisibilityState` parameter; change its internal `Text` rendering the amount to `MaskableAmountText`, preserving its existing `color`/styling.

Update all three call sites:
```kotlin
OverviewStat(
    label = LocalizationUtils.getTranslation("income", language),
    amount = formatInr(totalIncome),
    color = SemanticSuccess,
    modifier = Modifier.weight(1f),
)
```
→
```kotlin
OverviewStat(
    label = LocalizationUtils.getTranslation("income", language),
    amount = totalIncome,
    amountVisibility = amountVisibility,
    color = SemanticSuccess,
    modifier = Modifier.weight(1f),
)
```
```kotlin
OverviewStat(
    label = LocalizationUtils.getTranslation("expenses", language),
    amount = formatInr(totalExpenses),
    color = SemanticError,
    modifier = Modifier.weight(1f),
)
```
→
```kotlin
OverviewStat(
    label = LocalizationUtils.getTranslation("expenses", language),
    amount = totalExpenses,
    amountVisibility = amountVisibility,
    color = SemanticError,
    modifier = Modifier.weight(1f),
)
```
```kotlin
OverviewStat(
    label = "Net Balance",
    amount = "${if (totalBalance >= 0) "+" else "-"}${formatInr(kotlin.math.abs(totalBalance))}",
    color = if (totalBalance >= 0) SemanticSuccess else SemanticError,
    modifier = Modifier.weight(1f),
)
```
→
```kotlin
OverviewStat(
    label = "Net Balance",
    amount = kotlin.math.abs(totalBalance),
    amountVisibility = amountVisibility,
    prefix = if (totalBalance >= 0) "+" else "-",
    color = if (totalBalance >= 0) SemanticSuccess else SemanticError,
    modifier = Modifier.weight(1f),
)
```
This last one needs `OverviewStat` to also accept a `prefix: String = ""` parameter that it forwards to its internal `MaskableAmountText`'s own `prefix` — add that too.

- [ ] **Step 3: `InsightCard` helper**

Find `InsightCard`'s declaration (parameter `value: String`) and change to `value: Double` plus `amountVisibility: AmountVisibilityState`; internal rendering to `MaskableAmountText`.

Find:
```kotlin
InsightCard(
    title = "Average Daily Spend",
    value = formatInr(avgDaily),
    desc = "Calculated over a range of $daysCount day(s).",
    icon = Icons.Default.Wallet,
    accentColor = BrandBlue
)
```
Replace with:
```kotlin
InsightCard(
    title = "Average Daily Spend",
    value = avgDaily,
    amountVisibility = amountVisibility,
    desc = "Calculated over a range of $daysCount day(s).",
    icon = Icons.Default.Wallet,
    accentColor = BrandBlue
)
```

- [ ] **Step 4: `SpendingTrendChart`'s "Peak" label**

Find:
```kotlin
                Text(
                    text = "Peak: ${formatInr(points[peakIndex].spent)}" + " · ${points[peakIndex].label}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeutralBlack,
                )
```
Replace with:
```kotlin
                Row {
                    Text(text = "Peak: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralBlack)
                    MaskableAmountText(
                        amount = points[peakIndex].spent,
                        visibility = amountVisibility,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeutralBlack,
                    )
                    Text(text = " · ${points[peakIndex].label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralBlack)
                }
```
This chart composable needs `amountVisibility: AmountVisibilityState` added to its own parameters and threaded from its call site.

- [ ] **Step 5: Category breakdown row**

Find:
```kotlin
                Text(
                    text = "${formatInr(amount)}  ·  %,.1f%%".format(pct),
                    fontSize = 12.sp,
                    color = NeutralMid,
                )
```
Replace with:
```kotlin
                Row {
                    MaskableAmountText(
                        amount = amount,
                        visibility = amountVisibility,
                        fontSize = 12.sp,
                        color = NeutralMid,
                    )
                    Text(text = "  ·  %,.1f%%".format(pct), fontSize = 12.sp, color = NeutralMid)
                }
```
Thread `amountVisibility` into this composable the same way as prior steps.

- [ ] **Step 6: `ComparisonBar` helper**

Find `ComparisonBar`'s declaration (parameter `formattedValue: String`) and change to `amount: Double` plus `amountVisibility: AmountVisibilityState`; internal rendering to `MaskableAmountText`.

Find:
```kotlin
ComparisonBar(
    formattedValue = formatInr(income.toDouble()),
    label = "Income",
    fraction = income / maxVal,
    color = SemanticSuccess,
    trackColor = trackColor,
    modifier = Modifier.weight(1f),
)
ComparisonBar(
    formattedValue = formatInr(expense.toDouble()),
    label = "Expenses",
    fraction = expense / maxVal,
    color = SemanticError,
    trackColor = trackColor,
    modifier = Modifier.weight(1f),
)
```
Replace with:
```kotlin
ComparisonBar(
    amount = income.toDouble(),
    amountVisibility = amountVisibility,
    label = "Income",
    fraction = income / maxVal,
    color = SemanticSuccess,
    trackColor = trackColor,
    modifier = Modifier.weight(1f),
)
ComparisonBar(
    amount = expense.toDouble(),
    amountVisibility = amountVisibility,
    label = "Expenses",
    fraction = expense / maxVal,
    color = SemanticError,
    trackColor = trackColor,
    modifier = Modifier.weight(1f),
)
```

- [ ] **Step 7: Budget screen — total, spent, remaining**

Find:
```kotlin
                Text(
                    text = formatInr(totalBudget),
                    color = NeutralBlack,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
```
Replace with:
```kotlin
                MaskableAmountText(
                    amount = totalBudget,
                    visibility = amountVisibility,
                    color = NeutralBlack,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
```
Find:
```kotlin
                Text(
                    text = "Spent: ${formatInr(totalSpent)}",
                    fontSize = 12.sp,
                    color = NeutralMid
                )
```
Replace with:
```kotlin
                Row {
                    Text(text = "Spent: ", fontSize = 12.sp, color = NeutralMid)
                    MaskableAmountText(amount = totalSpent, visibility = amountVisibility, fontSize = 12.sp, color = NeutralMid)
                }
```
Find:
```kotlin
                Text(
                    text = "Remaining: ${formatInr((totalBudget - totalSpent).coerceAtLeast(0.0))}",
                    fontSize = 12.sp,
                    color = if (totalSpent > totalBudget) Color.Red else BrandBlue
                )
```
Replace with:
```kotlin
                Row {
                    Text(
                        text = "Remaining: ",
                        fontSize = 12.sp,
                        color = if (totalSpent > totalBudget) Color.Red else BrandBlue,
                    )
                    MaskableAmountText(
                        amount = (totalBudget - totalSpent).coerceAtLeast(0.0),
                        visibility = amountVisibility,
                        fontSize = 12.sp,
                        color = if (totalSpent > totalBudget) Color.Red else BrandBlue,
                    )
                }
```

- [ ] **Step 8: Per-category budget row (two amounts in one line)**

Find:
```kotlin
                Text(
                    text = "${formatInr(spent)} / ${formatInr(limit)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = NeutralBlack
                )
```
Replace with:
```kotlin
                Row {
                    MaskableAmountText(amount = spent, visibility = amountVisibility, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NeutralBlack)
                    Text(text = " / ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NeutralBlack)
                    MaskableAmountText(amount = limit, visibility = amountVisibility, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NeutralBlack)
                }
```

- [ ] **Step 9: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: fails only on `MainScreen.kt`'s calls to `AnalyticsScreen`/`BudgetScreen` missing `amountVisibility` (Task 6's job) — confirm no other errors, same as Task 3 Step 9.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/placeholder/PlaceholderScreens.kt
git commit -m "feat: mask amounts on Analytics and Budget behind the shared visibility session"
```

---

### Task 5: Holds, HoldDetail, Profile retrofit + Privacy Settings section

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/holds/HoldsScreen.kt`
- Modify: `app/src/main/java/com/example/spendsync/ui/holds/HoldDetailScreen.kt`
- Modify: `app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `MaskableAmountText`/`AmountVisibilityState`/`PinSetupDialog` (Task 2).
- Produces: `HoldsScreen(..., amountVisibility: AmountVisibilityState, ...)`, `HoldDetailScreen(..., amountVisibility: AmountVisibilityState, ...)` — new required parameters, wired by Task 6. `ProfileScreen` does NOT get a new required parameter for this — it reads `sessionDataStore.amountMaskingEnabled` etc. directly, same as its other settings rows, but DOES need `amountVisibility` threaded too since it has two `formatInr` sites of its own.

Before editing, re-read all three files to confirm each site below still matches.

- [ ] **Step 1: HoldsScreen — thread the parameter and mask the net-amount row**

Add `amountVisibility: AmountVisibilityState,` to `HoldsScreen`'s parameter list and the corresponding imports. Find:
```kotlin
                                    Text(
                                        formatInr(kotlin.math.abs(person.netAmount)),
                                        fontSize = 16.sp,
                                        color = NeutralBlack,
                                    )
```
Replace with:
```kotlin
                                    MaskableAmountText(
                                        amount = kotlin.math.abs(person.netAmount),
                                        visibility = amountVisibility,
                                        fontSize = 16.sp,
                                        color = NeutralBlack,
                                    )
```
Then find where this file renders `HoldDetailScreen(...)` and add `amountVisibility = amountVisibility` to that call.

- [ ] **Step 2: HoldDetailScreen — thread the parameter and mask both amount sites**

Add `amountVisibility: AmountVisibilityState,` to `HoldDetailScreen`'s parameter list and the corresponding imports. Find:
```kotlin
                                Text(
                                    formatInr(hold.amount.toDoubleOrNull() ?: 0.0),
                                    fontSize = 16.sp,
                                    color = NeutralBlack,
                                )
```
Replace with:
```kotlin
                                MaskableAmountText(
                                    amount = hold.amount.toDoubleOrNull() ?: 0.0,
                                    visibility = amountVisibility,
                                    fontSize = 16.sp,
                                    color = NeutralBlack,
                                )
```
Find:
```kotlin
                    Text(
                        "This removes tracking for ${formatInr(hold.amount.toDoubleOrNull() ?: 0.0)} with $personName. This can't be undone.",
                        fontSize = 13.sp,
                        color = NeutralMid,
                    )
```
Replace with:
```kotlin
                    Row {
                        Text("This removes tracking for ", fontSize = 13.sp, color = NeutralMid)
                        MaskableAmountText(
                            amount = hold.amount.toDoubleOrNull() ?: 0.0,
                            visibility = amountVisibility,
                            fontSize = 13.sp,
                            color = NeutralMid,
                        )
                        Text(" with $personName. This can't be undone.", fontSize = 13.sp, color = NeutralMid)
                    }
```
(`Row`'s default layout will wrap this across lines fine since it's plain text flow within a dialog card — if it renders awkwardly with 3 separate Text nodes not wrapping as one paragraph, that's an acceptable minor visual difference from before, not a blocker.)

- [ ] **Step 3: ProfileScreen — thread the parameter and mask the two stat amounts**

Add `amountVisibility: AmountVisibilityState,` to `ProfileScreen`'s parameter list and the corresponding imports. Find `StatItem`'s declaration (parameter `value: String`) and change to `value: Double` plus `amountVisibility: AmountVisibilityState`; internal rendering to `MaskableAmountText`.

Find:
```kotlin
                StatItem(
                    label  = "This Month",
                    value  = formatInr(currentMonthSpent),
                    modifier = Modifier.weight(1f),
                )
```
Replace with:
```kotlin
                StatItem(
                    label  = "This Month",
                    value  = currentMonthSpent,
                    amountVisibility = amountVisibility,
                    modifier = Modifier.weight(1f),
                )
```
Find:
```kotlin
                StatItem(
                    label  = "Savings",
                    value  = formatInr(savingsAccumulated),
                    modifier = Modifier.weight(1f),
                )
```
Replace with:
```kotlin
                StatItem(
                    label  = "Savings",
                    value  = savingsAccumulated,
                    amountVisibility = amountVisibility,
                    modifier = Modifier.weight(1f),
                )
```

- [ ] **Step 4: ProfileScreen — add the Privacy Settings section**

Add state near the file's other dialog-visibility booleans (alongside `showDateFormatDialog`):
```kotlin
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showVisibilityDurationDialog by remember { mutableStateOf(false) }
```

Add reactive reads near the other `sessionDataStore.*.collectAsState(...)` lines:
```kotlin
    val amountMaskingEnabled by sessionDataStore.amountMaskingEnabled.collectAsState(initial = false)
    val amountVisibilityDurationSeconds by sessionDataStore.amountVisibilityDurationSeconds.collectAsState(initial = 60)
```

Add a new section, following the exact `SectionHeader`/`ProfileMenuCard`/`SettingsToggleRow`/`SettingsDivider`/`SettingsNavigationRow` nesting the "Preferences" section already uses — place it as a new card directly after that section:

```kotlin
            // ── Privacy ──────────────────────────────────────────────────────
            SectionHeader("Privacy")
            ProfileMenuCard {
                SettingsToggleRow(
                    icon    = Icons.Default.Lock,
                    label   = "Hide large amounts",
                    sub     = "Mask amounts over ₹1,000 behind a PIN",
                    checked = amountMaskingEnabled,
                    onToggle = { turningOn ->
                        if (turningOn) {
                            showPinSetupDialog = true
                        } else {
                            scope.launch { sessionDataStore.updateAmountMaskingEnabled(false) }
                        }
                    },
                )
                if (amountMaskingEnabled) {
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Default.Lock,
                        label = "Change PIN",
                        onClick = { showChangePinDialog = true }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Default.Timer,
                        label = "Visibility duration",
                        sub = when (amountVisibilityDurationSeconds) {
                            30 -> "30 seconds"
                            300 -> "5 minutes"
                            900 -> "15 minutes"
                            else -> "1 minute"
                        },
                        onClick = { showVisibilityDurationDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
```

Add the three dialogs near the file's other `if (showXDialog) { ... }` blocks:

```kotlin
    if (showPinSetupDialog) {
        PinSetupDialog(
            sessionDataStore = sessionDataStore,
            requireCurrentPin = false,
            onDone = {
                showPinSetupDialog = false
                scope.launch { sessionDataStore.updateAmountMaskingEnabled(true) }
            },
            onDismiss = { showPinSetupDialog = false },
        )
    }

    if (showChangePinDialog) {
        PinSetupDialog(
            sessionDataStore = sessionDataStore,
            requireCurrentPin = true,
            onDone = { showChangePinDialog = false },
            onDismiss = { showChangePinDialog = false },
        )
    }

    if (showVisibilityDurationDialog) {
        OptionSelectionDialog(
            title = "Visibility duration",
            options = listOf("30 seconds", "1 minute", "5 minutes", "15 minutes"),
            selectedOption = when (amountVisibilityDurationSeconds) {
                30 -> "30 seconds"
                300 -> "5 minutes"
                900 -> "15 minutes"
                else -> "1 minute"
            },
            onDismiss = { showVisibilityDurationDialog = false },
            onSelect = { picked ->
                val seconds = when (picked) {
                    "30 seconds" -> 30
                    "5 minutes" -> 300
                    "15 minutes" -> 900
                    else -> 60
                }
                scope.launch {
                    sessionDataStore.updateAmountVisibilityDurationSeconds(seconds)
                    showVisibilityDurationDialog = false
                }
            }
        )
    }
```

Add the import `import com.example.spendsync.ui.shared.PinSetupDialog` and `import androidx.compose.material.icons.filled.Timer` (check `Icons.Default.Lock` is already imported — `ProfileScreen.kt` likely already has it for another row; add it if not).

- [ ] **Step 5: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: fails only on `MainScreen.kt`'s calls to `HoldsScreen`/`HomeScreen`/`AnalyticsScreen`/`BudgetScreen`/`ProfileScreen` missing `amountVisibility` where applicable (Task 6's job — note `ProfileScreen` also needs `amountVisibility` threaded from `MainScreen.kt`, since Step 3 added it as a required param) — confirm no other errors.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/holds/HoldsScreen.kt app/src/main/java/com/example/spendsync/ui/holds/HoldDetailScreen.kt app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt
git commit -m "feat: mask hold and profile amounts, add Privacy settings section"
```

---

### Task 6: MainScreen wiring

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `AmountVisibilityState`/`PinUnlockDialog` (Task 2), and the new `amountVisibility` parameters added to `HomeScreen`/`AnalyticsScreen`/`BudgetScreen`/`ProfileScreen`/`HoldsScreen` (Tasks 3-5) and `HoldDetailScreen` (already threaded internally by `HoldsScreen` in Task 5, not called directly from `MainScreen`).

Before editing, re-read the file to confirm its current structure (it's been touched by the previous feature's Task 5 this session) matches what's described below.

- [ ] **Step 1: Instantiate the shared state and the auto-lock timer**

Add near `val dateFilterState = remember { DateFilterState() }`:
```kotlin
    val amountVisibility = remember { AmountVisibilityState() }

    LaunchedEffect(amountVisibility.unlockedUntil) {
        val until = amountVisibility.unlockedUntil ?: return@LaunchedEffect
        val remainingMs = java.time.Duration.between(java.time.Instant.now(), until).toMillis()
        if (remainingMs > 0) {
            kotlinx.coroutines.delay(remainingMs)
        }
        amountVisibility.lock()
    }
```
Add `import com.example.spendsync.ui.shared.AmountVisibilityState` and `import com.example.spendsync.ui.shared.PinUnlockDialog`.

- [ ] **Step 2: Thread `amountVisibility` into every tab and overlay**

Add `amountVisibility = amountVisibility,` to the existing calls to `AnalyticsScreen(...)`, `BudgetScreen(...)`, `ProfileScreen(...)`, `HomeScreen(...)` (the `else -> HomeScreen(...)` branch), and `HoldsScreen(...)` (inside the Holds overlay's `AnimatedContent` block).

- [ ] **Step 3: Render the shared unlock dialog**

Add alongside the existing `if (dateFilterState.showMonthPicker) { MonthPickerDialog(...) }` block:
```kotlin
            if (amountVisibility.showUnlockPrompt) {
                PinUnlockDialog(
                    sessionDataStore = sessionDataStore,
                    onUnlock = { durationSeconds -> amountVisibility.unlock(durationSeconds) },
                    onDismiss = { amountVisibility.dismissUnlockPrompt() },
                )
            }
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — this should resolve every "missing amountVisibility argument" error left over from Tasks 3-5.

- [ ] **Step 5: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass, including the 10 new tests from Task 1 (`PinHashingTest` + `AmountMaskingTest`).

- [ ] **Step 6: Manually verify on-device**

Run: `/run` (or `./gradlew installDebug` + launch manually)

1. Open Profile → Privacy → toggle "Hide large amounts" on. Confirm the PIN-setup dialog appears (new PIN → confirm PIN), and after saving, the toggle is on.
2. Go to Home. Confirm any amount over ₹1000 shows as `★★★★★` with a small eye icon; amounts ≤ ₹1000 show normally.
3. Tap an eye icon. Confirm the unlock PIN dialog appears; enter the wrong PIN, confirm an error shows; enter the correct PIN, confirm ALL masked amounts across Home (balance card, tiles, transaction rows) reveal simultaneously.
4. Switch to Analytics, Budget, and the Holds screen without closing the app — confirm they're ALSO unlocked (shared session), not independently masked.
5. Wait out the configured visibility duration (set it to 30 seconds in Settings first to make this fast) — confirm everything automatically re-masks with no interaction needed.
6. Go to Profile → Privacy → Change PIN. Confirm it asks for the current PIN first, rejects a wrong one, then accepts a new PIN (entered twice).
7. Sign out and back in (or restart the app) — confirm masked amounts start masked again (session doesn't survive restart) and that the PIN still works (wasn't cleared) if it's the same account, or needs re-setup if signing into a different account.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/main/MainScreen.kt
git commit -m "feat: wire amount visibility session into MainScreen and all tabs"
```
