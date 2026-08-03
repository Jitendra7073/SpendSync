# Notification-Based Transaction Auto-Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-create transactions by reading payment notifications from allowlisted apps (UPI apps, banking apps, default SMS app), then follow up with an inline-reply notification asking the user for a description.

**Architecture:** A `NotificationListenerService` filters incoming notifications by an app allowlist the user configures in Settings, parses payment text with a generic regex heuristic, dedupes near-simultaneous duplicate signals, and creates the transaction via the existing `FinanceRepository`/API. Failed creates (e.g. offline) queue locally and retry via `WorkManager`. Every successful auto-capture triggers a follow-up notification with a `RemoteInput` inline-reply action that fills in the transaction's note.

**Tech Stack:** Kotlin, Jetpack Compose, `NotificationListenerService`, `NotificationCompat`/`RemoteInput`, `WorkManager` (new dependency), existing `SessionDataStore` (DataStore Preferences) and `FinanceRepository` (Retrofit + Gson).

Spec: `docs/superpowers/specs/2026-08-03-notification-auto-capture-design.md`

## Global Constraints

- Category on auto-captured transactions is always `"Other"` (existing fallback convention, see `AddExpenseScreen.kt:699-700`).
- Unparseable notifications (no confidently-extracted amount + direction) are dropped silently — never create a partial/garbage transaction.
- Dedup window is 5 minutes, matched on `(amount, direction)`.
- Feature is off by default; both the master toggle and each source app are opt-in.
- New Kotlin files live under `app/src/main/java/com/example/spendsync/notifications/`.
- Follow existing code style in this codebase: no KDoc on obvious code, comments only for non-obvious "why".

---

### Task 1: NotificationTransactionParser

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/NotificationTransactionParser.kt`
- Test: `app/src/test/java/com/example/spendsync/notifications/NotificationTransactionParserTest.kt`

**Interfaces:**
- Produces: `enum class TransactionDirection { DEBIT, CREDIT }`; `data class ParsedTransaction(val amount: Double, val direction: TransactionDirection, val payee: String?, val refNumber: String?)`; `object NotificationTransactionParser { fun parse(text: String): ParsedTransaction? }`. Later tasks (RecentCaptures, PendingCapture, the listener service) consume `TransactionDirection` and `ParsedTransaction` from this file.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.spendsync.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTransactionParserTest {

    @Test
    fun `parses a real bank UPI debit SMS`() {
        val text = "Dear UPI user A/C X0790 debited by 30.00 on date 03Aug26 trf to " +
            "MANISH DINESHPRA Refno 838274000171 If not u? call-1800111109 for other services-18001234-SBI"

        val result = NotificationTransactionParser.parse(text)

        assertEquals(30.00, result?.amount)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals("MANISH DINESHPRA", result?.payee)
        assertEquals("838274000171", result?.refNumber)
    }

    @Test
    fun `parses a UPI app payment notification with currency symbol`() {
        val result = NotificationTransactionParser.parse("You paid ₹500 to Swiggy")

        assertEquals(500.0, result?.amount)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals("Swiggy", result?.payee)
        assertNull(result?.refNumber)
    }

    @Test
    fun `parses a credit notification with a payer name`() {
        val result = NotificationTransactionParser.parse("Received ₹1200 from Rahul Sharma via UPI")

        assertEquals(1200.0, result?.amount)
        assertEquals(TransactionDirection.CREDIT, result?.direction)
        assertEquals("Rahul Sharma", result?.payee)
    }

    @Test
    fun `returns null when there is no direction keyword`() {
        assertNull(NotificationTransactionParser.parse("Your OTP is 493821, do not share it."))
    }

    @Test
    fun `returns null when there is no amount`() {
        assertNull(NotificationTransactionParser.parse("Your account was credited successfully."))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.NotificationTransactionParserTest"`
Expected: FAIL — `NotificationTransactionParser` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.example.spendsync.notifications

enum class TransactionDirection { DEBIT, CREDIT }

data class ParsedTransaction(
    val amount: Double,
    val direction: TransactionDirection,
    val payee: String?,
    val refNumber: String?,
)

/**
 * Generic regex/keyword heuristic for extracting a transaction from a
 * notification's raw text (title + text concatenated). Deliberately not a
 * per-bank template system — formats vary too much to enumerate. Returns
 * null whenever amount or direction can't be confidently found, rather
 * than guessing.
 */
object NotificationTransactionParser {

    private val DEBIT_KEYWORDS = Regex("""\b(debited|paid|spent)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_KEYWORDS = Regex("""\b(credited|received)\b""", RegexOption.IGNORE_CASE)
    private val CURRENCY_AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val KEYWORD_AMOUNT = Regex("""\b(?:by|of)\s+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val REF_NUMBER = Regex(
        """(?:Refno|Ref\s?No\.?|UPI\s?Ref|Txn\s?ID)\s*[:\-]?\s*(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val PAYEE_PATTERNS = listOf(
        Regex("""\btrf to\s+([A-Za-z][A-Za-z ]*?)(?=\s+Refno|\s+on\s+date|\s+via|\s+for|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z][A-Za-z ]*?)(?=\s+Refno|\s+on\s+date|\s+via|\s+for|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+([A-Za-z][A-Za-z ]*?)(?=\s+Refno|\s+on\s+date|\s+via|\s+for|\.|$)""", RegexOption.IGNORE_CASE),
    )

    fun parse(text: String): ParsedTransaction? {
        val direction = extractDirection(text) ?: return null
        val amount = extractAmount(text) ?: return null
        return ParsedTransaction(
            amount = amount,
            direction = direction,
            payee = extractPayee(text),
            refNumber = REF_NUMBER.find(text)?.groupValues?.get(1),
        )
    }

    private fun extractDirection(text: String): TransactionDirection? {
        val debit = DEBIT_KEYWORDS.find(text)
        val credit = CREDIT_KEYWORDS.find(text)
        return when {
            debit != null && credit == null -> TransactionDirection.DEBIT
            credit != null && debit == null -> TransactionDirection.CREDIT
            debit != null && credit != null ->
                if (debit.range.first <= credit.range.first) TransactionDirection.DEBIT else TransactionDirection.CREDIT
            else -> null
        }
    }

    private fun extractAmount(text: String): Double? {
        val raw = CURRENCY_AMOUNT.find(text)?.groupValues?.get(1)
            ?: KEYWORD_AMOUNT.find(text)?.groupValues?.get(1)
            ?: return null
        return raw.replace(",", "").toDoubleOrNull()
    }

    private fun extractPayee(text: String): String? {
        for (pattern in PAYEE_PATTERNS) {
            val match = pattern.find(text)?.groupValues?.get(1)?.trim()
            if (!match.isNullOrBlank()) return match
        }
        return null
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.NotificationTransactionParserTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/NotificationTransactionParser.kt app/src/test/java/com/example/spendsync/notifications/NotificationTransactionParserTest.kt
git commit -m "feat: add generic notification transaction parser"
```

---

### Task 2: RecentCaptures dedup buffer

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/RecentCaptures.kt`
- Test: `app/src/test/java/com/example/spendsync/notifications/RecentCapturesTest.kt`

**Interfaces:**
- Consumes: `TransactionDirection` from Task 1.
- Produces: `class RecentCaptures(windowMillis: Long = 300_000L) { fun isDuplicate(amount: Double, direction: TransactionDirection, nowMillis: Long): Boolean; fun record(amount: Double, direction: TransactionDirection, nowMillis: Long) }`. The listener service (Task 8) holds one instance for its lifetime.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.spendsync.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentCapturesTest {

    @Test
    fun `not a duplicate when buffer is empty`() {
        val recentCaptures = RecentCaptures()
        assertFalse(recentCaptures.isDuplicate(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L))
    }

    @Test
    fun `same amount and direction within the window is a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertTrue(recentCaptures.isDuplicate(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L + 60_000L))
    }

    @Test
    fun `same amount and direction outside the window is not a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertFalse(recentCaptures.isDuplicate(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L + 400_000L))
    }

    @Test
    fun `different amount is not a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertFalse(recentCaptures.isDuplicate(31.0, TransactionDirection.DEBIT, nowMillis = 1_000L))
    }

    @Test
    fun `different direction is not a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertFalse(recentCaptures.isDuplicate(30.0, TransactionDirection.CREDIT, nowMillis = 1_000L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.RecentCapturesTest"`
Expected: FAIL — `RecentCaptures` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.example.spendsync.notifications

/**
 * In-memory, service-lifetime dedup buffer. Covers the case where a single
 * real-world payment fires two notifications (e.g. the bank's SMS and the
 * UPI app's own confirmation) — the second one within [windowMillis] of a
 * matching amount+direction is treated as a duplicate.
 */
class RecentCaptures(private val windowMillis: Long = 300_000L) {

    private data class Signature(val amount: Double, val direction: TransactionDirection, val timestampMillis: Long)

    private val recent = mutableListOf<Signature>()

    fun isDuplicate(amount: Double, direction: TransactionDirection, nowMillis: Long): Boolean {
        recent.removeAll { nowMillis - it.timestampMillis > windowMillis }
        return recent.any { it.amount == amount && it.direction == direction }
    }

    fun record(amount: Double, direction: TransactionDirection, nowMillis: Long) {
        recent.add(Signature(amount, direction, nowMillis))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.RecentCapturesTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/RecentCaptures.kt app/src/test/java/com/example/spendsync/notifications/RecentCapturesTest.kt
git commit -m "feat: add in-memory dedup buffer for auto-captured transactions"
```

---

### Task 3: Auto-capture settings storage (allowlist + master toggle)

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/NotificationAppAllowlist.kt`
- Modify: `app/src/main/java/com/example/spendsync/data/local/SessionDataStore.kt`
- Test: `app/src/test/java/com/example/spendsync/data/local/AutoCapturePackagesParsingTest.kt`

**Interfaces:**
- Produces: `data class AllowlistedApp(val packageName: String, val displayName: String)`; `object NotificationAppAllowlist { val APPS: List<AllowlistedApp> }`. On `SessionDataStore`: `val autoCaptureEnabled: Flow<Boolean>`, `val autoCapturePackages: Flow<Set<String>>`, `suspend fun updateAutoCaptureEnabled(enabled: Boolean)`, `suspend fun updateAutoCapturePackages(packages: Set<String>)`. Consumed by the listener service (Task 8) and Settings UI (Task 9).

- [ ] **Step 1: Write the failing test for the pure parse/serialize functions**

```kotlin
package com.example.spendsync.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCapturePackagesParsingTest {

    @Test
    fun `parses null as empty set`() {
        assertTrue(parseAutoCapturePackages(null).isEmpty())
    }

    @Test
    fun `parses blank string as empty set`() {
        assertTrue(parseAutoCapturePackages("").isEmpty())
    }

    @Test
    fun `round trips a set of package names`() {
        val packages = setOf("com.phonepe.app", "com.google.android.apps.messaging")

        val serialized = serializeAutoCapturePackages(packages)
        val parsed = parseAutoCapturePackages(serialized)

        assertEquals(packages, parsed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.data.local.AutoCapturePackagesParsingTest"`
Expected: FAIL — `parseAutoCapturePackages`/`serializeAutoCapturePackages` unresolved.

- [ ] **Step 3: Create the allowlist file**

```kotlin
package com.example.spendsync.notifications

data class AllowlistedApp(val packageName: String, val displayName: String)

/**
 * Curated seed list for the Settings "Auto-detect transactions" per-app
 * toggles. Deliberately not user-extensible in v1 (see design spec's open
 * questions) — an app not on this list simply isn't offered as a toggle.
 */
object NotificationAppAllowlist {
    val APPS = listOf(
        AllowlistedApp("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        AllowlistedApp("com.phonepe.app", "PhonePe"),
        AllowlistedApp("net.one97.paytm", "Paytm"),
        AllowlistedApp("com.google.android.apps.messaging", "Messages"),
        AllowlistedApp("com.samsung.android.messaging", "Samsung Messages"),
        AllowlistedApp("com.android.mms", "Messaging"),
    )
}
```

- [ ] **Step 4: Add the pure parse/serialize functions and DataStore wiring to `SessionDataStore.kt`**

Add near the top of the file, alongside `parsePersistedCategories`/`serializePersistedCategories` (after line 35):

```kotlin
internal fun parseAutoCapturePackages(raw: String?): Set<String> =
    raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

internal fun serializeAutoCapturePackages(packages: Set<String>): String =
    packages.joinToString(",")
```

Add two new keys to the `companion object` (after `KEY_CUSTOM_EXPENSE_CATEGORIES`, line 63):

```kotlin
        private val KEY_AUTO_CAPTURE_ENABLED  = booleanPreferencesKey("auto_capture_enabled")
        private val KEY_AUTO_CAPTURE_PACKAGES = stringPreferencesKey("auto_capture_packages")
```

Add two new read flows, after `customExpenseCategories` (line 123):

```kotlin
    val autoCaptureEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CAPTURE_ENABLED] ?: false
    }

    val autoCapturePackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseAutoCapturePackages(prefs[KEY_AUTO_CAPTURE_PACKAGES])
    }
```

Add two new write functions, after `addCustomExpenseCategory` (line 224):

```kotlin
    suspend fun updateAutoCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_CAPTURE_ENABLED] = enabled }
    }

    suspend fun updateAutoCapturePackages(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_CAPTURE_PACKAGES] = serializeAutoCapturePackages(packages) }
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.data.local.AutoCapturePackagesParsingTest"`
Expected: PASS (all 3 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/NotificationAppAllowlist.kt app/src/main/java/com/example/spendsync/data/local/SessionDataStore.kt app/src/test/java/com/example/spendsync/data/local/AutoCapturePackagesParsingTest.kt
git commit -m "feat: add auto-capture app allowlist and settings storage"
```

---

### Task 4: PendingCapture model and local pending queue

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/PendingCapture.kt`
- Modify: `app/src/main/java/com/example/spendsync/data/local/SessionDataStore.kt`
- Test: `app/src/test/java/com/example/spendsync/notifications/PendingCaptureSerializationTest.kt`

**Interfaces:**
- Consumes: `TransactionDirection` from Task 1.
- Produces: `data class PendingCapture(val amount: Double, val direction: TransactionDirection, val payee: String?, val sourceApp: String, val capturedAtMillis: Long)`. On `SessionDataStore`: `val pendingCaptures: Flow<List<PendingCapture>>`, `suspend fun addPendingCapture(capture: PendingCapture)`, `suspend fun removePendingCapture(capture: PendingCapture)`. Consumed by the listener service (Task 8) and the retry worker (Task 7).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.spendsync.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCaptureSerializationTest {

    @Test
    fun `parses null as empty list`() {
        assertTrue(parsePendingCaptures(null).isEmpty())
    }

    @Test
    fun `parses blank string as empty list`() {
        assertTrue(parsePendingCaptures("").isEmpty())
    }

    @Test
    fun `round trips a list of pending captures`() {
        val captures = listOf(
            PendingCapture(30.0, TransactionDirection.DEBIT, "MANISH DINESHPRA", "com.phonepe.app", 1_000L),
            PendingCapture(500.0, TransactionDirection.CREDIT, null, "com.google.android.apps.messaging", 2_000L),
        )

        val serialized = serializePendingCaptures(captures)
        val parsed = parsePendingCaptures(serialized)

        assertEquals(captures, parsed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.PendingCaptureSerializationTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.example.spendsync.notifications

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PendingCapture(
    val amount: Double,
    val direction: TransactionDirection,
    val payee: String?,
    val sourceApp: String,
    val capturedAtMillis: Long,
)

private val pendingCaptureGson = Gson()
private val pendingCaptureListType = object : TypeToken<List<PendingCapture>>() {}.type

internal fun serializePendingCaptures(captures: List<PendingCapture>): String =
    pendingCaptureGson.toJson(captures)

internal fun parsePendingCaptures(raw: String?): List<PendingCapture> {
    if (raw.isNullOrBlank()) return emptyList()
    return pendingCaptureGson.fromJson<List<PendingCapture>>(raw, pendingCaptureListType) ?: emptyList()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.PendingCaptureSerializationTest"`
Expected: PASS (all 3 tests)

- [ ] **Step 5: Add the pending queue to `SessionDataStore.kt`**

Add a new key to the `companion object`:

```kotlin
        private val KEY_PENDING_CAPTURES = stringPreferencesKey("pending_captures")
```

Add near the other read flows (needs `import com.example.spendsync.notifications.PendingCapture`, `import com.example.spendsync.notifications.parsePendingCaptures`, `import com.example.spendsync.notifications.serializePendingCaptures`):

```kotlin
    val pendingCaptures: Flow<List<PendingCapture>> = context.dataStore.data.map { prefs ->
        parsePendingCaptures(prefs[KEY_PENDING_CAPTURES])
    }
```

Add near the other write functions:

```kotlin
    suspend fun addPendingCapture(capture: PendingCapture) {
        context.dataStore.edit { prefs ->
            val current = parsePendingCaptures(prefs[KEY_PENDING_CAPTURES])
            prefs[KEY_PENDING_CAPTURES] = serializePendingCaptures(current + capture)
        }
    }

    suspend fun removePendingCapture(capture: PendingCapture) {
        context.dataStore.edit { prefs ->
            val current = parsePendingCaptures(prefs[KEY_PENDING_CAPTURES])
            prefs[KEY_PENDING_CAPTURES] = serializePendingCaptures(current - capture)
        }
    }
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/PendingCapture.kt app/src/main/java/com/example/spendsync/data/local/SessionDataStore.kt app/src/test/java/com/example/spendsync/notifications/PendingCaptureSerializationTest.kt
git commit -m "feat: add pending-capture model and local retry queue"
```

---

### Task 5: NotificationReplyReceiver

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/NotificationCaptureIds.kt`
- Create: `app/src/main/java/com/example/spendsync/notifications/NotificationReplyReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `internal object NotificationCaptureIds { const val CHANNEL_ID; const val EXTRA_TRANSACTION_ID; const val KEY_TEXT_REPLY }`; `class NotificationReplyReceiver : BroadcastReceiver()`. Consumed by `TransactionCaptureNotifier` (Task 6), which builds the `Intent` targeting this receiver.
- Consumes: `FinanceRepository.updateTransaction(id: String, note: String? = ...)` (existing, `FinanceRepository.kt:129-146`).

No dedicated unit test — a `BroadcastReceiver` reading system `RemoteInput` extras isn't practically unit-testable without an instrumented test harness this project doesn't have. Verified manually in Task 8's on-device check.

- [ ] **Step 1: Create the shared constants file**

```kotlin
package com.example.spendsync.notifications

internal object NotificationCaptureIds {
    const val CHANNEL_ID = "auto_capture_channel"
    const val EXTRA_TRANSACTION_ID = "transaction_id"
    const val KEY_TEXT_REPLY = "key_text_reply"
}
```

- [ ] **Step 2: Create the receiver**

```kotlin
package com.example.spendsync.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the inline "add a description" reply on a follow-up notification
 * posted by [TransactionCaptureNotifier]. Constructs its own repository the
 * same way [android.app.Application]-less background components do
 * elsewhere in this app — there's no app-wide DI container.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(NotificationCaptureIds.EXTRA_TRANSACTION_ID) ?: return
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationCaptureIds.KEY_TEXT_REPLY)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return

        val pendingResult = goAsync()
        val sessionDataStore = SessionDataStore(context.applicationContext)
        val financeRepository = FinanceRepository(sessionDataStore)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                financeRepository.updateTransaction(id = transactionId, note = reply)
                NotificationManagerCompat.from(context).cancel(transactionId.hashCode())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 3: Register the receiver in `AndroidManifest.xml`**

Add inside `<application>`, after the closing `</provider>` tag (line 49):

```xml
        <receiver
            android:name=".notifications.NotificationReplyReceiver"
            android:exported="false" />
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/NotificationCaptureIds.kt app/src/main/java/com/example/spendsync/notifications/NotificationReplyReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add inline-reply receiver for auto-captured transaction descriptions"
```

---

### Task 6: TransactionCaptureNotifier

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/TransactionCaptureNotifier.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `NotificationCaptureIds`, `NotificationReplyReceiver` (Task 5); `TransactionDto` (existing, `data/remote/model/AppModels.kt:26-37`).
- Produces: `object TransactionCaptureNotifier { fun postDescriptionRequest(context: Context, transaction: TransactionDto) }`. Consumed by the listener service (Task 8) and the retry worker (Task 7).

No dedicated unit test — builds real `Notification`/`RemoteInput` objects, requires the Android framework. Verified manually in Task 8's on-device check.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.example.spendsync.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.spendsync.R
import com.example.spendsync.data.remote.model.TransactionDto

/**
 * Posts the "add a description" follow-up notification after a transaction
 * is auto-captured. Notifications never carry a user-authored note, so this
 * fires for every successful auto-capture, not just low-confidence ones.
 */
object TransactionCaptureNotifier {

    private const val ACTION_ADD_DESCRIPTION = "com.example.spendsync.ACTION_ADD_DESCRIPTION"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationCaptureIds.CHANNEL_ID,
                "Auto-captured transactions",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun postDescriptionRequest(context: Context, transaction: TransactionDto) {
        ensureChannel(context)

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_ADD_DESCRIPTION
            putExtra(NotificationCaptureIds.EXTRA_TRANSACTION_ID, transaction.id)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            transaction.id.hashCode(),
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "Add description",
            replyPendingIntent,
        ).addRemoteInput(
            RemoteInput.Builder(NotificationCaptureIds.KEY_TEXT_REPLY)
                .setLabel("Add a description")
                .build()
        ).build()

        val directionSign = if (transaction.type == "credit") "+" else "-"
        val notification = NotificationCompat.Builder(context, NotificationCaptureIds.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$directionSign₹${transaction.amount} to ${transaction.merchant}")
            .setContentText("Tap reply to add a description")
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(transaction.id.hashCode(), notification)
    }
}
```

- [ ] **Step 2: Declare the `POST_NOTIFICATIONS` permission in `AndroidManifest.xml`**

Add after the existing `INTERNET` permission (line 5):

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/TransactionCaptureNotifier.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add follow-up description-request notification"
```

---

### Task 7: WorkManager dependency and PendingCaptureRetryWorker

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/spendsync/notifications/PendingCaptureRetryWorker.kt`

**Interfaces:**
- Consumes: `SessionDataStore.pendingCaptures`/`removePendingCapture` (Task 4), `FinanceRepository.createTransaction` (existing, `FinanceRepository.kt:106-127`), `TransactionCaptureNotifier.postDescriptionRequest` (Task 6).
- Produces: `class PendingCaptureRetryWorker : CoroutineWorker` with `companion object { fun schedule(context: Context) }`. Consumed by the listener service (Task 8) whenever a create call fails.

No dedicated unit test — `CoroutineWorker` needs `androidx.work:work-testing` (androidTest, not set up in this project) to test meaningfully. Verified manually in Task 8's on-device check (turn off network, trigger a notification, turn network back on, confirm the transaction eventually appears).

- [ ] **Step 1: Add the WorkManager version and library to `gradle/libs.versions.toml`**

Add to `[versions]` (after `coil = "3.3.0"`):

```toml
work = "2.10.0"
```

Add to `[libraries]` (after the Coil block):

```toml
# WorkManager — retries transaction creation for auto-captured notifications
# received while offline.
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
```

- [ ] **Step 2: Add the dependency to `app/build.gradle.kts`**

Add after `implementation(libs.accompanist.systemuicontroller)` (line 83):

```kotlin
    // WorkManager — retries transaction creation for auto-captured notifications
    implementation(libs.androidx.work.runtime.ktx)
```

- [ ] **Step 3: Write the worker**

```kotlin
package com.example.spendsync.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class PendingCaptureRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionDataStore = SessionDataStore(applicationContext)
        val financeRepository = FinanceRepository(sessionDataStore)
        val pending = sessionDataStore.pendingCaptures.firstOrNull().orEmpty()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (capture in pending) {
            val type = if (capture.direction == TransactionDirection.DEBIT) "debit" else "credit"
            when (val result = financeRepository.createTransaction(
                amount = capture.amount,
                type = type,
                merchant = capture.payee ?: "Unknown",
                category = "Other",
                sourceApp = capture.sourceApp,
            )) {
                is AuthResult.Success -> {
                    sessionDataStore.removePendingCapture(capture)
                    TransactionCaptureNotifier.postDescriptionRequest(applicationContext, result.data)
                }
                is AuthResult.Error -> anyFailed = true
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "pending_capture_retry"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingCaptureRetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **Step 4: Sync Gradle and build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/spendsync/notifications/PendingCaptureRetryWorker.kt
git commit -m "feat: retry auto-capture transaction creation via WorkManager when offline"
```

---

### Task 8: TransactionNotificationListenerService

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/TransactionNotificationListenerService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `NotificationTransactionParser` (Task 1), `RecentCaptures` (Task 2), `SessionDataStore.autoCaptureEnabled`/`autoCapturePackages`/`addPendingCapture` (Tasks 3–4), `FinanceRepository.createTransaction` (existing), `TransactionCaptureNotifier.postDescriptionRequest` (Task 6), `PendingCaptureRetryWorker.schedule` (Task 7).

No dedicated unit test — `NotificationListenerService.onNotificationPosted` requires a live `StatusBarNotification` from the system; not constructible in a JVM unit test. Verified manually below.

- [ ] **Step 1: Write the service**

```kotlin
package com.example.spendsync.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class TransactionNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentCaptures = RecentCaptures()
    private lateinit var sessionDataStore: SessionDataStore
    private lateinit var financeRepository: FinanceRepository

    override fun onCreate() {
        super.onCreate()
        sessionDataStore = SessionDataStore(applicationContext)
        financeRepository = FinanceRepository(sessionDataStore)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val text = extractText(sbn.notification) ?: return

        serviceScope.launch {
            if (sessionDataStore.autoCaptureEnabled.firstOrNull() != true) return@launch
            val enabledPackages = sessionDataStore.autoCapturePackages.firstOrNull().orEmpty()
            if (packageName !in enabledPackages) return@launch

            val parsed = NotificationTransactionParser.parse(text) ?: return@launch
            val now = System.currentTimeMillis()
            if (recentCaptures.isDuplicate(parsed.amount, parsed.direction, now)) return@launch

            val type = if (parsed.direction == TransactionDirection.DEBIT) "debit" else "credit"
            when (val result = financeRepository.createTransaction(
                amount = parsed.amount,
                type = type,
                merchant = parsed.payee ?: "Unknown",
                category = "Other",
                sourceApp = packageName,
            )) {
                is AuthResult.Success -> {
                    recentCaptures.record(parsed.amount, parsed.direction, now)
                    TransactionCaptureNotifier.postDescriptionRequest(applicationContext, result.data)
                }
                is AuthResult.Error -> {
                    sessionDataStore.addPendingCapture(
                        PendingCapture(parsed.amount, parsed.direction, parsed.payee, packageName, now)
                    )
                    PendingCaptureRetryWorker.schedule(applicationContext)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun extractText(notification: Notification): String? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return "$title $body".trim().ifBlank { null }
    }
}
```

- [ ] **Step 2: Register the service in `AndroidManifest.xml`**

Add inside `<application>`, after the `<receiver>` added in Task 5:

```xml
        <service
            android:name=".notifications.TransactionNotificationListenerService"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/TransactionNotificationListenerService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add notification listener service for transaction auto-capture"
```

---

### Task 9: Settings UI — Automation section

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `SessionDataStore.autoCaptureEnabled`/`autoCapturePackages`/`updateAutoCaptureEnabled`/`updateAutoCapturePackages` (Task 3), `NotificationAppAllowlist.APPS` (Task 3).

No dedicated unit test — Compose UI wired to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` and a runtime permission launcher isn't unit-testable in this project (no Compose UI tests exist for `ProfileScreen` today). Verified manually below.

- [ ] **Step 1: Add new imports**

Add to the import block (after `import android.content.Intent` at line 3):

```kotlin
import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import com.example.spendsync.notifications.NotificationAppAllowlist
```

- [ ] **Step 2: Add state for the new section**

Add after the `dateFormat` line (line 141):

```kotlin
    val autoCaptureEnabled by sessionDataStore.autoCaptureEnabled.collectAsState(initial = false)
    val autoCapturePackages by sessionDataStore.autoCapturePackages.collectAsState(initial = emptySet())
```

Add after `var showExportDialog by remember { mutableStateOf(false) }` (line 169):

```kotlin
    var showAutoCaptureExplainer by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: the follow-up notification just won't show if denied */ }
```

- [ ] **Step 3: Add the Automation section**

Insert after the "Preferences" card's closing `Spacer(Modifier.height(20.dp))` (line 426), before `SectionHeader("Data")` (line 429):

```kotlin
            // ── Automation ───────────────────────────────────────────────────
            SectionHeader("Automation")
            ProfileMenuCard {
                SettingsToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    label = "Auto-detect transactions",
                    sub = "Reads payment notifications from apps you choose below",
                    checked = autoCaptureEnabled,
                    onToggle = { turningOn ->
                        if (turningOn) {
                            showAutoCaptureExplainer = true
                        } else {
                            scope.launch { sessionDataStore.updateAutoCaptureEnabled(false) }
                        }
                    },
                )
                if (autoCaptureEnabled) {
                    NotificationAppAllowlist.APPS.forEach { app ->
                        SettingsDivider()
                        SettingsToggleRow(
                            icon = Icons.Default.NotificationsActive,
                            label = app.displayName,
                            checked = app.packageName in autoCapturePackages,
                            onToggle = { checked ->
                                val updated = if (checked) autoCapturePackages + app.packageName
                                    else autoCapturePackages - app.packageName
                                scope.launch { sessionDataStore.updateAutoCapturePackages(updated) }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
```

- [ ] **Step 4: Add the explainer dialog**

Insert after the `showExportDialog` block (after line 580's closing, before the next section):

```kotlin
    if (showAutoCaptureExplainer) {
        BasicAlertDialog(onDismissRequest = { showAutoCaptureExplainer = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Notification Access Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralBlack,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "SpendSync needs permission to read notifications so it can detect payments " +
                            "automatically. Only the apps you select below are read — nothing else, and " +
                            "nothing is sent anywhere else.",
                        fontSize = 13.sp,
                        color = NeutralMid,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAutoCaptureExplainer = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            showAutoCaptureExplainer = false
                            scope.launch { sessionDataStore.updateAutoCaptureEnabled(true) }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
```

This block uses `context`, which isn't yet bound at the top of `ProfileScreen` (only inside the export dialog composable). Add `val context = LocalContext.current` alongside the other top-level `val`s (next to `val scope = rememberCoroutineScope()` at line 119).

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Manually verify the full feature on-device**

Run: `/run` (or `./gradlew installDebug` + launch manually)

1. Open Profile → Automation → toggle "Auto-detect transactions" on. Confirm the explainer dialog appears, then confirm it opens the system Notification Access settings screen.
2. In system settings, grant SpendSync Notification Access. Return to the app.
3. Enable the "Messages" (or whichever SMS/UPI app you have installed) toggle under Automation.
4. Trigger a real or test notification from that app containing text like `"debited by 30.00 ... trf to TEST PAYEE Refno 12345"`.
5. Confirm: a new transaction appears in the transaction list with the right amount/direction/category "Other", AND a follow-up notification appears asking for a description.
6. Reply to the follow-up notification inline with a description. Confirm the transaction's note is updated (pull-to-refresh or reopen the transaction detail).
7. Turn off Wi-Fi/mobile data, trigger another notification, confirm no crash and no transaction appears yet. Turn network back on, wait a few seconds, confirm the transaction appears (WorkManager retry) along with its own follow-up notification.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt
git commit -m "feat: add auto-capture settings UI with permission onboarding"
```
