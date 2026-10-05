package com.example.spendsync.ui.planify

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.planify.PlanMath
import com.example.spendsync.data.remote.model.BucketDto
import com.example.spendsync.data.remote.model.PlanItemRequest
import com.example.spendsync.data.remote.model.PlanViewDto
import com.example.spendsync.data.remote.model.SavePlanRequest
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.home.HeroCard
import com.example.spendsync.ui.home.HeroChip
import com.example.spendsync.ui.home.HomeSectionHeader
import com.example.spendsync.ui.home.HomeTopBar
import com.example.spendsync.ui.home.animatedAmount
import com.example.spendsync.ui.home.animatedFraction
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.home.introIn
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.search.GlobalSearchDialog
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.ui.transaction.expenseCategories
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private sealed interface Page {
    data object Home : Page
    data class Builder(val month: String, val seed: PlanViewDto?, val editing: Boolean) : Page
    data class Bucket(val category: String) : Page
}

private fun requestOf(plan: PlanViewDto, change: (List<PlanItemRequest>) -> List<PlanItemRequest>) = SavePlanRequest(
    income = plan.income,
    carryOver = plan.carryOver,
    items = change(plan.status.buckets.mapIndexed { i, b -> PlanItemRequest(b.category, b.name, b.kind, b.limit, i, b.rollover) }),
)

/**
 * Planify: decide how the month's money is split, then watch it. Home shows what is safe to spend today and every
 * bucket's state; the builder makes (or edits, or copies) the plan; a bucket opens in detail. Limits are soft:
 * going over is shown and explained, never blocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanifyScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState,
    amountVisibility: AmountVisibilityState,
    onOpenSettings: () -> Unit = {},
    onViewTransaction: (TransactionDto) -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    /** Bump to open the plan builder (from the Home card or the salary notification). */
    buildRequestId: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val month = remember(dateFilterState.selectedDate) { PlanMath.monthKey(dateFilterState.selectedDate) }
    val today = remember { LocalDate.now().toString() }
    val monthDate = remember(month) { LocalDate.parse("$month-01") }

    var plan by remember { mutableStateOf<PlanViewDto?>(null) }
    var previous by remember { mutableStateOf<PlanViewDto?>(null) }
    var monthTxs by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var page by remember { mutableStateOf<Page>(Page.Home) }
    var showSearch by remember { mutableStateOf(false) }
    var moveSheet by remember { mutableStateOf<Triple<String?, String?, Double?>?>(null) }
    var overFor by remember { mutableStateOf<BucketDto?>(null) }
    var editLimitFor by remember { mutableStateOf<BucketDto?>(null) }
    var addPreset by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var intro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { intro = true }
    BackHandler(enabled = page != Page.Home) { page = Page.Home }
    val covers = page != Page.Home
    androidx.compose.runtime.DisposableEffect(covers) {
        com.example.spendsync.notifications.PlanifyLinks.coversBottomBar.value = covers
        onDispose { com.example.spendsync.notifications.PlanifyLinks.coversBottomBar.value = false }
    }

    suspend fun load(force: Boolean) {
        when (val r = financeRepository.getPlan(month, today, force)) {
            is AuthResult.Success -> plan = r.data
            is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
        }
        val start = monthDate.toString() + "T00:00:00.000Z"
        val end = monthDate.withDayOfMonth(monthDate.lengthOfMonth()).toString() + "T23:59:59.999Z"
        (financeRepository.getTransactions(startDate = start, endDate = end, limit = 500, forceRefresh = force) as? AuthResult.Success)?.let { monthTxs = it.data }
        if (plan?.exists == false) {
            previous = (financeRepository.getPlan(PlanMath.previousMonth(month), today, force) as? AuthResult.Success)?.data?.takeIf { it.exists }
        } else previous = null
    }

    LaunchedEffect(month) {
        loading = true
        page = Page.Home
        load(false)
        loading = false
    }
    LaunchedEffect(buildRequestId) {
        if (buildRequestId > 0) page = Page.Builder(month, plan?.takeIf { it.exists }, editing = plan?.exists == true)
    }

    /** Applies a server answer: show the new plan, or explain what went wrong. */
    fun applied(r: AuthResult<PlanViewDto>, okMessage: Int? = null) {
        when (r) {
            is AuthResult.Success -> { plan = r.data; okMessage?.let { toast = ToastMessage(tr(it), isError = false) } }
            is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
        }
    }

    fun saveWith(p: PlanViewDto, okMessage: Int? = null, change: (List<PlanItemRequest>) -> List<PlanItemRequest>) {
        scope.launch { applied(financeRepository.savePlan(month, requestOf(p, change)), okMessage) }
    }

    val custom by sessionDataStore.customExpenseCategories.collectAsState(initial = emptyList())
    // Categories the user can actually pick: the built-in ones plus their own (not the plan-only "Savings"/"Other").
    val realCategories = remember(custom) { expenseCategories.map { it.label } + custom.map { it.name } }
    val missing = remember(plan, realCategories) {
        com.example.spendsync.data.planify.SmartCategories.missing(plan?.status?.buckets?.map { it.category }.orEmpty(), realCategories).toSet()
    }
    val known = remember(custom) { (expenseCategories.map { it.label } + custom.map { it.name } + listOf("Savings", "Other")).distinct() }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != Page.Home
                (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
            },
            label = "planify_nav",
        ) { current ->
            when (current) {
                is Page.Builder -> PlanBuilder(
                    month = current.month, seed = current.seed, editing = current.editing,
                    financeRepository = financeRepository, sessionDataStore = sessionDataStore, vis = amountVisibility,
                    onSaved = { saved ->
                        if (current.month != month) dateFilterState.selectDate(LocalDate.parse("${current.month}-01")) else plan = saved
                        page = Page.Home
                        toast = ToastMessage(tr(R.string.pl_plan_saved), isError = false)
                    },
                    onCancel = { page = Page.Home },
                )
                is Page.Bucket -> {
                    val p = plan
                    val b = p?.status?.buckets?.firstOrNull { it.category == current.category }
                    if (p != null && b != null) {
                        BucketDetailPage(
                            bucket = b, plan = p, month = monthDate, monthTxs = monthTxs, vis = amountVisibility,
                            canMove = p.status.buckets.size > 1,
                            onBack = { page = Page.Home },
                            onEditLimit = { editLimitFor = b },
                            onMove = { moveSheet = Triple(b.category, null, null) },
                            onRemove = { page = Page.Home; saveWith(p, R.string.pl_bucket_removed) { items -> items.filter { it.category != b.category } } },
                            onViewTransaction = onViewTransaction,
                            matches = p.matches.filter { it.bucket == b.category },
                            missingCategory = b.category in missing,
                            onCreateCategory = {
                                scope.launch {
                                    sessionDataStore.addCustomExpenseCategory(b.category, com.example.spendsync.data.planify.SmartCategories.iconFor(b.category))
                                    toast = ToastMessage(tr(R.string.pl_cat_created), isError = false)
                                }
                            },
                            onAnswerMatch = { m, verdict ->
                                scope.launch { applied(financeRepository.answerPlanMatch(month, com.example.spendsync.data.remote.model.MatchAnswerRequest(m.bucket, m.kind, m.label, verdict)), R.string.pl_match_thanks) }
                            },
                            onForget = { a ->
                                scope.launch { applied(financeRepository.answerPlanMatch(month, com.example.spendsync.data.remote.model.MatchAnswerRequest(a.bucket, a.kind, a.label, "forget")), R.string.pl_alias_forgot) }
                            },
                        )
                    } else page = Page.Home
                }
                Page.Home -> PlanHome(
                    plan = plan, previous = previous, loading = loading, refreshing = refreshing, monthTxs = monthTxs,
                    monthDate = monthDate, intro = intro, vis = amountVisibility, dateFilterState = dateFilterState,
                    onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } },
                    onSearch = { showSearch = true },
                    onAssistant = onOpenAssistant,
                    onBuild = { page = Page.Builder(month, null, editing = false) },
                    onCopyPrevious = { page = Page.Builder(month, previous, editing = false) },
                    onEdit = { page = Page.Builder(month, plan, editing = true) },
                    onCopyToNext = { plan?.let { page = Page.Builder(PlanMath.nextMonth(month), it, editing = false) } },
                    onOpenBucket = { page = Page.Bucket(it.category) },
                    onMove = { moveSheet = Triple(null, null, null) },
                    onFix = { overFor = it },
                    onAddBucket = { preset -> addPreset = preset; showAdd = true },
                    onToSavings = { p ->
                        saveWith(p) { items ->
                            val extra = p.status.leftToPlan
                            if (items.any { it.category == "Savings" }) items.map { if (it.category == "Savings") it.copy(limitAmount = it.limitAmount + extra) else it }
                            else items + PlanItemRequest("Savings", "Savings", "savings", extra, items.size)
                        }
                    },
                    onDelete = { confirmDelete = true },
                    missingCategories = missing,
                    onCreateCategory = { name ->
                        scope.launch {
                            sessionDataStore.addCustomExpenseCategory(name, com.example.spendsync.data.planify.SmartCategories.iconFor(name))
                            toast = ToastMessage(tr(R.string.pl_cat_created), isError = false)
                        }
                    },
                    onAnswerMatch = { m, verdict ->
                        scope.launch { applied(financeRepository.answerPlanMatch(month, com.example.spendsync.data.remote.model.MatchAnswerRequest(m.bucket, m.kind, m.label, verdict)), R.string.pl_match_thanks) }
                    },
                    onTune = { from, to, amount -> scope.launch { applied(financeRepository.movePlanMoney(month, from, to, amount), R.string.pl_moved) } },
                )
            }
        }

        val p = plan
        moveSheet?.let { (from, to, amount) ->
            if (p != null) MoveMoneySheet(
                buckets = p.status.buckets, from = from, to = to, suggestedAmount = amount, vis = amountVisibility,
                onMove = { f, t, a ->
                    moveSheet = null
                    scope.launch { applied(financeRepository.movePlanMoney(month, f, t, a), R.string.pl_moved) }
                },
                onDismiss = { moveSheet = null },
            )
        }
        overFor?.let { b ->
            if (p != null) OverLimitSheet(
                bucket = b,
                donors = p.status.buckets.filter { it.kind != "fixed" },
                vis = amountVisibility,
                onMove = { donor ->
                    overFor = null
                    scope.launch { applied(financeRepository.movePlanMoney(month, donor.category, b.category, -b.remaining), R.string.pl_moved) }
                },
                onRaise = {
                    overFor = null
                    val newLimit = kotlin.math.ceil(b.spent / 10.0) * 10.0
                    saveWith(p, R.string.pl_limit_raised) { items -> items.map { if (it.category == b.category) it.copy(limitAmount = newLimit) else it } }
                },
                onDismiss = { overFor = null },
            )
        }
        editLimitFor?.let { b ->
            if (p != null) EditLimitDialog(
                bucket = b,
                onSave = { v ->
                    editLimitFor = null
                    saveWith(p, R.string.pl_limit_raised) { items -> items.map { if (it.category == b.category) it.copy(limitAmount = v) else it } }
                },
                onDismiss = { editLimitFor = null },
            )
        }
        if (confirmDelete) {
            com.example.spendsync.ui.components.AppConfirmDialog(
                title = tr(R.string.pl_delete_title, monthDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))),
                message = tr(R.string.pl_delete_body),
                confirmLabel = tr(R.string.delete),
                cancelLabel = tr(R.string.cancel),
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    scope.launch { applied(financeRepository.deletePlan(month), R.string.pl_plan_deleted) }
                },
                onDismiss = { confirmDelete = false },
            )
        }
        if (showAdd && p != null) {
            AddBucketDialog(
                known = (listOfNotNull(addPreset) + known).distinct(),
                taken = p.status.buckets.map { it.category }.toSet(),
                presetCategory = addPreset,
                onAdd = { category, kind, limit ->
                    showAdd = false
                    saveWith(p, R.string.pl_bucket_added) { items -> items + PlanItemRequest(category, category, kind, limit, items.size) }
                },
                onDismiss = { showAdd = false },
            )
        }
        if (showSearch) {
            GlobalSearchDialog(
                financeRepository = financeRepository,
                sessionDataStore = sessionDataStore,
                amountVisibility = amountVisibility,
                onDismiss = { showSearch = false },
                onTransactionSelected = { tx -> showSearch = false; onViewTransaction(tx) },
                onOpenSettings = { showSearch = false; onOpenSettings() },
            )
        }
    }
}

// ── Home ─────────────────────────────────────────────────────────────────────

private val PlaceholderBucket = BucketDto("p", "Groceries", "Groceries", "spend", 5000.0, 3100.0, 0, false, 1900.0, 62.0, "ok", null, null, null, false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanHome(
    plan: PlanViewDto?,
    previous: PlanViewDto?,
    loading: Boolean,
    refreshing: Boolean,
    monthTxs: List<TransactionDto>,
    monthDate: LocalDate,
    intro: Boolean,
    vis: AmountVisibilityState,
    dateFilterState: DateFilterState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onAssistant: () -> Unit,
    onBuild: () -> Unit,
    onCopyPrevious: () -> Unit,
    onEdit: () -> Unit,
    onCopyToNext: () -> Unit,
    onOpenBucket: (BucketDto) -> Unit,
    onMove: () -> Unit,
    onFix: (BucketDto) -> Unit,
    onAddBucket: (String?) -> Unit,
    onToSavings: (PlanViewDto) -> Unit,
    onDelete: () -> Unit,
    onTune: (from: String, to: String, amount: Double) -> Unit,
    missingCategories: Set<String>,
    onCreateCategory: (String) -> Unit,
    onAnswerMatch: (com.example.spendsync.data.remote.model.MatchDto, String) -> Unit,
) {
    val status = plan?.status
    SettingsBackdrop {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SettingsContentWidth {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.statusBarsPadding()) {
                            HomeTopBar(
                                title = tr(R.string.pl_title),
                                icon = Icons.Default.PieChart,
                                selectedDate = dateFilterState.selectedDate,
                                datePattern = "MMMM yyyy",
                                onCalendarClick = { dateFilterState.showMonthPicker = true },
                                onSearchClick = onSearch,
                                onAssistantClick = onAssistant,
                            )
                        }

                        if (loading || plan == null) {
                            Skeleton(loading = true) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    PlanHero(PlaceholderStatus, 50000.0, true, vis, {}, Modifier)
                                    repeat(3) { BucketCard(PlaceholderBucket, vis, onClick = {}) }
                                }
                            }
                        } else if (!plan.exists) {
                            EmptyPlan(monthDate, previous != null, onBuild, onCopyPrevious, Modifier.introIn(intro, 0))
                        } else if (status != null) {
                            PlanHero(status, plan.income, plan.hasIncome, vis, onEdit, Modifier.introIn(intro, 0))
                            if (plan.hasIncome && status.leftToPlan > 0.5) {
                                LeftoverCard(status.leftToPlan, vis, onSavings = { onToSavings(plan) }, onChoose = onEdit, modifier = Modifier.introIn(intro, 1))
                            }
                            CountsStrip(status.counts.ok, status.counts.close, status.counts.over, Modifier.introIn(intro, 1))

                            if (status.day >= 15 && status.daysLeft > 0) TuneUpCard(status.buckets, vis, onTune, Modifier.introIn(intro, 2))
                            if (status.unplannedTotal > 0.5) UnplannedCard(status.unplanned.take(3), status.unplannedTotal, vis, { onAddBucket(status.unplanned.firstOrNull()?.category) }, Modifier.introIn(intro, 2))

                            listOf("fixed", "spend", "savings").forEach { kind ->
                                val group = status.buckets.filter { it.kind == kind }
                                if (group.isNotEmpty()) {
                                    HomeSectionHeader(groupTitle(kind))
                                    group.forEach { b ->
                                        BucketCard(
                                            b, vis, onClick = { onOpenBucket(b) }, onFix = if (b.state == "over") ({ onFix(b) }) else null,
                                            needsConfirm = plan.matches.any { it.bucket == b.category },
                                            missingCategory = b.category in missingCategories,
                                        )
                                    }
                                }
                            }

                            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppButton(tr(R.string.pl_move_title), onClick = onMove, variant = ButtonVariant.Tonal, size = ButtonSize.Large, enabled = status.buckets.size > 1, modifier = Modifier.weight(1f))
                                AppButton(tr(R.string.pl_edit_plan), onClick = onEdit, variant = ButtonVariant.Outline, size = ButtonSize.Large, modifier = Modifier.weight(1f))
                            }

                            val spendCats = status.buckets.filter { it.kind == "spend" }
                            val spendLimit = spendCats.sumOf { it.limit }
                            if (spendLimit > 0.0) {
                                PaceCard(monthTxs.filter { t -> t.type == "debit" && spendCats.any { it.category == t.category } }, monthDate, spendLimit, vis)
                            }
                            if (status.buckets.isNotEmpty()) PlanVsSpentCard(status.buckets.sortedByDescending { it.percent }, vis)

                            AppButton(tr(R.string.pl_delete_plan), onClick = onDelete, variant = ButtonVariant.Text, modifier = Modifier.padding(horizontal = 16.dp))
                            if (status.daysLeft == 0) ReviewCard(status.planned, status.spentInPlan, status.buckets, vis, onCopyToNext)
                        }
                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }
}

private val PlaceholderStatus = com.example.spendsync.data.remote.model.PlanStatusDto(
    daysInMonth = 31, day = 14, daysLeft = 18, monthProgressPercent = 45.0, available = 50000.0, planned = 45000.0, leftToPlan = 5000.0,
    spentInPlan = 20000.0, buckets = emptyList(), counts = com.example.spendsync.data.remote.model.PlanCountsDto(3, 2, 1),
    safeToSpendToday = 256.0, unplanned = emptyList(), unplannedTotal = 0.0,
)

@Composable
private fun PlanHero(
    s: com.example.spendsync.data.remote.model.PlanStatusDto,
    income: Double,
    hasIncome: Boolean,
    vis: AmountVisibilityState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    val finished = s.daysLeft == 0
    val shownSafe = animatedAmount(if (finished) s.spentInPlan else s.safeToSpendToday)
    val progress = animatedFraction(if (s.planned > 0) (s.spentInPlan / s.planned).toFloat().coerceIn(0f, 1f) else 0f)

    HeroCard(modifier) {
        Text(tr(if (finished) R.string.pl_month_done else R.string.pl_safe_today), color = onHero.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        MaskableAmountText(shownSafe, vis, color = onHero, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (finished) tr(R.string.pl_spent_of_planned, formatInrSafe(vis, s.planned))
            else tr(R.string.pl_days_left, s.daysLeft),
            color = onHero.copy(alpha = 0.8f), fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(onHero.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(onHero))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroChip(tr(R.string.spent), s.spentInPlan, vis, Modifier.weight(1f))
            when {
                hasIncome && s.leftToPlan > 0.5 -> HeroChip(tr(R.string.pl_unassigned), s.leftToPlan, vis, Modifier.weight(1f), onClick = onEdit)
                hasIncome && s.leftToPlan < -0.5 -> HeroChip(tr(R.string.pl_over_planned), -s.leftToPlan, vis, Modifier.weight(1f), onClick = onEdit)
                else -> HeroChip(tr(R.string.pl_planned), s.planned, vis, Modifier.weight(1f))
            }
        }
    }
}

private fun formatInrSafe(vis: AmountVisibilityState, amount: Double) = safeText(vis, formatInr(amount))

@Composable
private fun CountsStrip(ok: Int, close: Int, over: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(vertical = 14.dp)) {
        listOf(
            Triple(ok, R.string.on_track, scheme.primary),
            Triple(close, R.string.near_limit, SemanticWarning),
            Triple(over, R.string.over, expenseColor()),
        ).forEach { (count, label, color) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(label), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyPlan(monthDate: LocalDate, hasPrevious: Boolean, onBuild: () -> Unit, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val monthName = monthDate.format(DateTimeFormatter.ofPattern("MMMM"))
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PieChart, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(30.dp))
        }
        Text(tr(R.string.pl_empty_title, monthName), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Text(tr(R.string.pl_empty_body), fontSize = 14.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        AppButton(tr(R.string.pl_empty_cta, monthName), onClick = onBuild, size = ButtonSize.Large, leadingIcon = Icons.Default.AutoAwesome, fullWidth = true)
        if (hasPrevious) AppButton(tr(R.string.pl_copy_last), onClick = onCopy, variant = ButtonVariant.Tonal, size = ButtonSize.Large, fullWidth = true)
    }
}

@Composable
private fun UnplannedCard(items: List<com.example.spendsync.data.remote.model.UnplannedDto>, total: Double, vis: AmountVisibilityState, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr(R.string.pl_unplanned_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SemanticWarning)
        Row {
            MaskableAmountText(total, vis, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text("  " + tr(R.string.pl_unplanned_sub), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        items.forEach { u ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(categoryLabel(u.category), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                MaskableAmountText(u.spent, vis, fontSize = 13.sp, color = scheme.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        AppButton(tr(R.string.pl_unplanned_add), onClick = onAdd, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.Add)
    }
}

/** Shown once the month is over: how it went, and one tap to start the next month from this plan. */
@Composable
private fun ReviewCard(planned: Double, spent: Double, buckets: List<BucketDto>, vis: AmountVisibilityState, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val onPlan = buckets.count { it.state != "over" }
    val worst = buckets.filter { it.state == "over" }.maxByOrNull { -it.remaining }
    val saved = buckets.filter { it.kind == "savings" }.sumOf { it.spent }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.pl_review_card_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Text(tr(R.string.pl_review_on_plan, onPlan, buckets.size), fontSize = 14.sp, color = incomeColor(), fontWeight = FontWeight.SemiBold)
        Text(tr(R.string.pl_review_numbers, formatInrSafe(vis, planned), formatInrSafe(vis, spent), formatInrSafe(vis, saved)), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        if (worst != null) Text(tr(R.string.pl_review_worst, worst.title(), formatInrSafe(vis, -worst.remaining)), fontSize = 13.sp, color = SemanticWarning)
        Spacer(Modifier.height(4.dp))
        AppButton(tr(R.string.pl_copy_next), onClick = onCopy, size = ButtonSize.Large, fullWidth = true)
    }
}

/** Money that has no bucket yet. Says so plainly and offers the two ways out: savings, or pick buckets. */
@Composable
private fun LeftoverCard(left: Double, vis: AmountVisibilityState, onSavings: () -> Unit, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr(R.string.pl_unassigned_banner_title, formatInrSafe(vis, left)), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SemanticWarning)
        Text(tr(R.string.pl_unassigned_banner_body), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(tr(R.string.pl_unassigned_savings), onClick = onSavings, size = ButtonSize.Small)
            AppButton(tr(R.string.pl_unassigned_edit), onClick = onChoose, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
        }
    }
}

/**
 * Halfway through the month, once there is real spending: if a bucket is on course to overshoot and another has
 * clear room, offer the one-tap move. This is how a thin-history plan gets tuned after a few weeks.
 */
@Composable
private fun TuneUpCard(buckets: List<BucketDto>, vis: AmountVisibilityState, onMove: (String, String, Double) -> Unit, modifier: Modifier = Modifier) {
    var dismissed by remember { mutableStateOf(false) }
    val tip = remember(buckets) {
        val needy = buckets.filter { it.kind == "spend" && it.state != "over" && (it.projected ?: 0.0) > it.limit * 1.1 }.maxByOrNull { (it.projected ?: 0.0) - it.limit }
        val gap = needy?.let { kotlin.math.ceil(((it.projected ?: 0.0) - it.limit) / 50.0) * 50.0 } ?: 0.0
        val donor = buckets.filter { it.kind != "fixed" && it.category != needy?.category }
            .maxByOrNull { it.limit - maxOf(it.spent, it.projected ?: it.spent) }
        val room = donor?.let { kotlin.math.floor((it.limit - maxOf(it.spent, it.projected ?: it.spent)) / 50.0) * 50.0 } ?: 0.0
        val amount = minOf(gap, room)
        if (needy != null && donor != null && amount >= 50.0) Triple(needy, donor, amount) else null
    }
    if (tip == null || dismissed) return
    val (needy, donor, amount) = tip
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr(R.string.pl_tune_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        Text(tr(R.string.pl_tune_pace, needy.title(), formatInrSafe(vis, needy.projected ?: 0.0), formatInrSafe(vis, needy.limit)), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(tr(R.string.pl_tune_move, formatInrSafe(vis, amount), donor.title()), onClick = { onMove(donor.category, needy.category, amount); dismissed = true }, size = ButtonSize.Small)
            AppButton(tr(R.string.pl_tune_not_now), onClick = { dismissed = true }, variant = ButtonVariant.Text, size = ButtonSize.Small)
        }
    }
}
