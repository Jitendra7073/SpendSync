package com.example.spendsync.ui.planify

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.PlanItemRequest
import com.example.spendsync.data.remote.model.PlanViewDto
import com.example.spendsync.data.remote.model.SavePlanRequest
import com.example.spendsync.data.remote.model.SuggestionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.ui.transaction.expenseCategories
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.launch

/** One editable row of the draft. The limit is text so a half-typed number never fights the keyboard. */
internal class DraftItem(val category: String, name: String, kind: String, limit: String, val average: Double? = null) {
    var name by mutableStateOf(name)
    var kind by mutableStateOf(kind)
    var limit by mutableStateOf(limit)
}

private fun plain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/**
 * The monthly plan in three steps: how much money, how to split it, a last look. Pre-filled from history (or from
 * the existing plan when editing, or last month's when copying), so it is a draft to approve, not a form to fill.
 * The counter always shows what is still unassigned; planning more than you have is allowed (limits are soft).
 */
@Composable
internal fun PlanBuilder(
    month: String,
    seed: PlanViewDto?,
    editing: Boolean,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    vis: AmountVisibilityState,
    onSaved: (PlanViewDto) -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var income by remember { mutableStateOf("") }
    var carry by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<DraftItem>() }
    var loading by remember { mutableStateOf(seed == null) }
    var suggestedIncome by remember { mutableStateOf(0.0) }
    var saving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val custom by sessionDataStore.customExpenseCategories.collectAsState(initial = emptyList())
    val known = remember(custom) { (expenseCategories.map { it.label } + custom.map { it.name } + listOf("Savings", "Other")).distinct() }

    fun fill(s: SuggestionDto) {
        items.clear()
        s.items.forEach { items += DraftItem(it.category, it.name, it.kind, plain(it.limit), average = it.average) }
        suggestedIncome = s.suggestedIncome
        if (income.isBlank() && s.suggestedIncome > 0) income = plain(s.suggestedIncome)
    }

    suspend fun loadSuggestions() {
        loading = true
        when (val r = financeRepository.getPlanSuggestions(month)) {
            is AuthResult.Success -> fill(r.data)
            is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        if (seed != null) {
            income = if (seed.income > 0) plain(seed.income) else ""
            seed.status.buckets.forEach { items += DraftItem(it.category, it.name, it.kind, plain(it.limit), average = if (editing) null else it.spent) }
            loading = false
        } else {
            loadSuggestions()
        }
    }

    val available = (amountOrNull(income) ?: 0.0) + (amountOrNull(carry) ?: 0.0)
    val planned = items.sumOf { amountOrNull(it.limit) ?: 0.0 }
    val left = available - planned

    fun back() { if (step > 0) step-- else onCancel() }

    fun save() {
        scope.launch {
            saving = true
            val ordered = items.sortedBy { kindOrder(it.kind) }
            val req = SavePlanRequest(
                income = amountOrNull(income) ?: 0.0,
                carryOver = amountOrNull(carry) ?: 0.0,
                items = ordered.mapIndexed { i, it ->
                    PlanItemRequest(it.category, it.name.ifBlank { it.category }, it.kind, amountOrNull(it.limit) ?: 0.0, i)
                },
            )
            when (val r = financeRepository.savePlan(month, req)) {
                is AuthResult.Success -> onSaved(r.data)
                is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
            }
            saving = false
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        SettingsBackdrop {
            Column(Modifier.fillMaxSize()) {
                SettingsTopBar(tr(if (editing) R.string.pl_edit_plan else R.string.pl_build_title), onBack = ::back)
                StepDots(step)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val fwd = targetState > initialState
                            (slideInHorizontally(tween(280)) { if (fwd) it / 5 else -it / 5 } + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(tween(200)) { if (fwd) -it / 5 else it / 5 } + fadeOut(tween(160)))
                        },
                        label = "builder_step",
                    ) { s ->
                        when (s) {
                            0 -> IncomeStep(income, { income = it.filter { c -> c.isDigit() || c == '.' } }, carry, { carry = it.filter { c -> c.isDigit() || c == '.' } }, suggestedIncome, loading) { income = plain(suggestedIncome) }
                            1 -> BucketsStep(items, available, left, loading, vis, editing, onAdd = { showAdd = true }, onRemove = { items.remove(it) }, onUseHistory = { scope.launch { loadSuggestions() } }, onRestToSavings = {
                                val existing = items.firstOrNull { it.category == "Savings" }
                                if (existing != null) existing.limit = plain((amountOrNull(existing.limit) ?: 0.0) + left)
                                else items += DraftItem("Savings", "Savings", "savings", plain(left))
                            })
                            else -> ReviewStep(items, available, planned, left, vis)
                        }
                    }
                }
                // Bottom actions
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (step > 0) AppButton(tr(R.string.back), onClick = ::back, variant = ButtonVariant.Outline, size = ButtonSize.Large, modifier = Modifier.weight(1f))
                    AppButton(
                        text = tr(if (step < 2) R.string.next else R.string.pl_start_plan),
                        onClick = { if (step < 2) step++ else save() },
                        size = ButtonSize.Large,
                        loading = saving,
                        enabled = !saving && !loading && when (step) { 0 -> available > 0; 1 -> items.isNotEmpty(); else -> true },
                        modifier = Modifier.weight(if (step > 0) 2f else 1f),
                    )
                }
            }
        }
        if (showAdd) {
            AddBucketDialog(
                known = known,
                taken = items.map { it.category }.toSet(),
                onAdd = { category, kind, limit ->
                    items += DraftItem(category, category, kind, plain(limit))
                    showAdd = false
                },
                onDismiss = { showAdd = false },
            )
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val color by animateColorAsState(if (i <= step) scheme.primary else scheme.outlineVariant, tween(250), label = "dot")
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun IncomeStep(
    income: String,
    onIncome: (String) -> Unit,
    carry: String,
    onCarry: (String) -> Unit,
    suggested: Double,
    loading: Boolean,
    onUseSuggested: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsContentWidth {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(tr(R.string.pl_income_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
                Text(tr(R.string.pl_income_sub), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(
                        value = income, onValueChange = onIncome, label = tr(R.string.pl_income_label),
                        keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next,
                    )
                    if (suggested > 0 && amountOrNull(income) != suggested) {
                        AppButton(tr(R.string.pl_use_detected, formatInr(suggested)), onClick = onUseSuggested, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.AutoAwesome)
                    }
                    AppTextField(
                        value = carry, onValueChange = onCarry, label = tr(R.string.pl_carry_label),
                        keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done,
                        supportingText = tr(R.string.pl_carry_hint),
                    )
                }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp).height(16.dp).width(16.dp), strokeWidth = 2.dp)
                    Text(tr(R.string.pl_reading_history), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BucketsStep(
    items: List<DraftItem>,
    available: Double,
    left: Double,
    loading: Boolean,
    vis: AmountVisibilityState,
    editing: Boolean,
    onAdd: () -> Unit,
    onRemove: (DraftItem) -> Unit,
    onUseHistory: () -> Unit,
    onRestToSavings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = when {
        left < -0.5 -> expenseColor()
        left > 0.5 -> SemanticWarning
        else -> incomeColor()
    }
    val toneAnimated by animateColorAsState(tone, tween(250), label = "left_tone")
    Column(Modifier.fillMaxSize()) {
        // The counter stays put while the list scrolls under it.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().glassCard().padding(16.dp)) {
            Text(
                tr(when { left < -0.5 -> R.string.pl_over_planned; left > 0.5 -> R.string.pl_left_to_plan; else -> R.string.pl_fully_planned }),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = toneAnimated,
            )
            Text(safeText(vis, formatInr(kotlin.math.abs(left))), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = toneAnimated)
            Text(tr(R.string.pl_of_available, safeText(vis, formatInr(available))), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsContentWidth {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 20.dp)) {
                            CircularProgressIndicator(Modifier.padding(end = 10.dp).height(18.dp).width(18.dp), strokeWidth = 2.dp)
                            Text(tr(R.string.pl_reading_history), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                    listOf("fixed", "spend", "savings").forEach { kind ->
                        val group = items.filter { it.kind == kind }
                        if (group.isNotEmpty()) {
                            Text(
                                groupTitle(kind).uppercase() + "  ·  " + safeText(vis, formatInr(group.sumOf { amountOrNull(it.limit) ?: 0.0 })),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                            group.forEach { item -> DraftRow(item, vis, onRemove = { onRemove(item) }) }
                        }
                    }
                    if (!loading && items.isEmpty()) Text(tr(R.string.pl_no_buckets), fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    Spacer(Modifier.height(6.dp))
                    AppButton(tr(R.string.pl_add_bucket_title), onClick = onAdd, variant = ButtonVariant.Tonal, leadingIcon = Icons.Default.Add, fullWidth = true)
                    if (left > 0.5) AppButton(tr(R.string.pl_rest_to_savings, safeText(vis, formatInr(left))), onClick = onRestToSavings, variant = ButtonVariant.Outline, fullWidth = true)
                    if (!editing) AppButton(tr(R.string.pl_use_history), onClick = onUseHistory, variant = ButtonVariant.Text, leadingIcon = Icons.Default.AutoAwesome, fullWidth = true, enabled = !loading)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DraftRow(item: DraftItem, vis: AmountVisibilityState, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().glassCard().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (item.name == item.category) categoryLabel(item.category) else item.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = scheme.onSurface, maxLines = 1)
            if (item.average != null && item.average > 0) {
                Text(tr(R.string.pl_last_month, safeText(vis, formatInr(item.average))), fontSize = 11.sp, color = scheme.onSurfaceVariant)
            }
        }
        AppTextField(
            value = item.limit,
            onValueChange = { item.limit = it.filter { c -> c.isDigit() || c == '.' } },
            label = "₹",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            modifier = Modifier.width(128.dp),
        )
        AppIconButton(Icons.Default.Close, tr(R.string.pl_remove), onClick = onRemove)
    }
}

@Composable
private fun ReviewStep(items: List<DraftItem>, available: Double, planned: Double, left: Double, vis: AmountVisibilityState) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsContentWidth {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(tr(R.string.pl_review_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
                Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewLine(tr(R.string.pl_review_money), safeText(vis, formatInr(available)))
                    ReviewLine(tr(R.string.pl_review_planned), safeText(vis, formatInr(planned)))
                    ReviewLine(
                        tr(if (left < -0.5) R.string.pl_over_planned else R.string.pl_review_unassigned),
                        safeText(vis, formatInr(kotlin.math.abs(left))),
                        color = if (left < -0.5) expenseColor() else scheme.onSurface,
                    )
                }
                if (left < -0.5) Text(tr(R.string.pl_review_soft), fontSize = 13.sp, color = SemanticWarning)
                listOf("fixed", "spend", "savings").forEach { kind ->
                    val group = items.filter { it.kind == kind }
                    if (group.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(groupTitle(kind), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                            group.forEach { ReviewLine(if (it.name == it.category) categoryLabel(it.category) else it.name, safeText(vis, formatInr(amountOrNull(it.limit) ?: 0.0))) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
