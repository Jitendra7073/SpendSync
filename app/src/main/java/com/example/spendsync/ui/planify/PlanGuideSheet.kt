package com.example.spendsync.ui.planify

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.planify.BackTest
import com.example.spendsync.data.planify.PlanGuide
import com.example.spendsync.data.remote.model.GuideQuestionDto
import com.example.spendsync.data.remote.model.GuideTurnItem
import com.example.spendsync.data.remote.model.GuideTurnRequest
import com.example.spendsync.data.remote.model.SuggestionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.AppLanguage
import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Q { Fixed, Save, Style, Buffer }

private fun needsAmountNone(e: com.example.spendsync.data.remote.model.GuideEffectDto) = e.type == "none"

private const val ANALYSING = -1
private const val THINKING = -2
private const val BUILDING = -3
private const val SUMMARY = -4

/** `seq` grows on every forward step, so the slide direction is right even when ids are not in order. */
private data class Scr(val id: Int, val seq: Int)

/**
 * The planning guide. With the assistant switched on, an AI model reads the user's own monthly totals and writes each
 * multiple-choice question about something specific in them (a category that jumped, a regular bill, savings);
 * what an option does is a small whitelisted effect that [PlanGuide] applies with plain arithmetic, so the model
 * never writes the numbers. Without consent, or if no model answers, a fixed set of questions is used instead.
 * The language menu re-asks the current question in the chosen language.
 */
@Composable
internal fun PlanGuideSheet(
    suggestion: SuggestionDto?,
    income: Double,
    month: String,
    aiAllowed: Boolean,
    financeRepository: FinanceRepository,
    vis: AmountVisibilityState,
    onApply: (PlanGuide.Result) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(LanguageManager.current) }
    var menu by remember { mutableStateOf(false) }
    val s = suggestion ?: SuggestionDto(emptyList(), 0.0, 0)

    var scr by remember { mutableStateOf(Scr(ANALYSING, 0)) }
    fun go(id: Int, back: Boolean = false) { scr = Scr(id, scr.seq + if (back) -1 else 1) }
    var picked by remember { mutableStateOf(-1) }
    var answers by remember { mutableStateOf(PlanGuide.Answers()) }

    // AI state: null = still finding out whether the AI will write the questions.
    var useAi by remember { mutableStateOf<Boolean?>(if (aiAllowed && income > 0) null else false) }
    val aiQs = remember { mutableStateListOf<GuideQuestionDto>() }
    val transcript = remember { mutableStateListOf<GuideTurnItem>() }
    var aiNote by remember { mutableStateOf<String?>(null) }
    var aiModel by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }

    val staticQs = remember(s) {
        buildList {
            if (PlanGuide.hasFixed(s)) add(Q.Fixed)
            add(Q.Save)
            if (PlanGuide.hasEveryday(s)) add(Q.Style)
            add(Q.Buffer)
        }
    }

    fun g(@StringRes id: Int, vararg args: Any) = LanguageManager.stringIn(language, id, *args)
    fun money(v: Double) = safeText(vis, formatInr(v))
    suspend fun ask(focus: String? = null) = financeRepository.planGuide(
        GuideTurnRequest(month, income, language.storedName, transcript.toList(), focus),
    )

    // The first question is requested while the "analysing" lines play, so the wait is covered by real work.
    LaunchedEffect(Unit) {
        if (useAi == null) {
            val r = (ask() as? AuthResult.Success)?.data
            val q = r?.question
            if (r != null && r.source == "ai" && !r.done && q != null) { aiQs += q; aiModel = r.model; useAi = true } else useAi = false
        }
    }

    // Switching language re-asks the question on screen (same topic) in the new language.
    var lastLanguage by remember { mutableStateOf(language) }
    LaunchedEffect(language) {
        if (language == lastLanguage) return@LaunchedEffect
        lastLanguage = language
        val i = scr.id
        if (useAi == true && i in aiQs.indices && picked < 0) {
            translating = true
            val r = (ask(focus = aiQs[i].topic) as? AuthResult.Success)?.data
            r?.question?.let { if (r.source == "ai" && !r.done && i in aiQs.indices) aiQs[i] = it }
            translating = false
        }
    }

    fun pickStatic(next: PlanGuide.Answers, option: Int) {
        if (picked >= 0) return
        picked = option
        scope.launch { delay(260); answers = next; picked = -1; go(scr.id + 1) }
    }

    // An option that needs a number from the person (a bill, an estimate) opens a small amount field.
    var pendingInput by remember { mutableStateOf<Int?>(null) }
    var inputText by remember { mutableStateOf("") }
    // Regular bills: the same question comes back after each one, until the person says that is all.
    val usedOptions = remember { mutableStateListOf<Int>() }
    val billLog = remember { mutableStateListOf<String>() }

    fun proceed(q: GuideQuestionDto, answerText: String) {
        scope.launch {
            delay(260)
            transcript += GuideTurnItem(q.topic, q.question, answerText)
            usedOptions.clear(); billLog.clear(); picked = -1; pendingInput = null; inputText = ""
            asking = true
            go(THINKING)
            val r = (ask() as? AuthResult.Success)?.data
            asking = false
            val next = r?.question
            if (r != null && r.source == "ai" && !r.done && next != null) { aiQs += next; go(aiQs.lastIndex) }
            else { aiNote = r?.note; go(BUILDING) }
        }
    }

    fun needsAmount(e: com.example.spendsync.data.remote.model.GuideEffectDto) = (e.type == "commitment" || e.type == "estimate") && e.value == null

    fun choose(q: GuideQuestionDto, option: Int) {
        if (picked >= 0 || translating) return
        val o = q.options[option]
        if (needsAmount(o.effect)) { pendingInput = option; inputText = ""; return }
        picked = option
        answers = PlanGuide.apply(answers, o.effect.type, o.effect.value, o.effect.category, o.effect.name)
        // "none" on the bills question ends the loop and reports every bill added so far
        proceed(q, if (q.topic.equals("commitments", true) && billLog.isNotEmpty()) billLog.joinToString("; ") else o.label)
    }

    fun confirmAmount(q: GuideQuestionDto) {
        val option = pendingInput ?: return
        val amount = inputText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 && it <= income * 3 } ?: return
        val o = q.options[option]
        answers = PlanGuide.apply(answers, o.effect.type, amount, o.effect.category, o.effect.name)
        val what = o.effect.name ?: o.effect.category ?: o.label
        if (o.effect.type == "commitment") {
            billLog += "$what ${formatInr(amount)}"
            usedOptions += option
            pendingInput = null; inputText = ""
            // nothing left to add? finish now
            if (q.options.indices.none { it !in usedOptions && !needsAmountNone(q.options[it].effect) }) proceed(q, billLog.joinToString("; "))
        } else {
            picked = option
            proceed(q, "$what ${formatInr(amount)}")
        }
    }

    AppSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Text(g(R.string.pg_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable { menu = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Translate, contentDescription = g(R.string.pg_language), tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Text(language.nativeName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary, modifier = Modifier.padding(start = 6.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    AppLanguage.entries.forEach { l ->
                        DropdownMenuItem(text = { Text(l.nativeName) }, onClick = { language = l; menu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        if (income <= 0.0) {
            Text(g(R.string.pg_no_income), fontSize = 15.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            AppButton(g(R.string.got_it), onClick = onDismiss, size = ButtonSize.Large, fullWidth = true)
            return@AppSheet
        }

        val fixedCount = s.items.count { it.kind == "fixed" }
        val savingsItem = s.items.firstOrNull { it.kind == "savings" }
        val analysisLines = buildList {
            if (s.monthsUsed == 0) add(g(R.string.pg_an_none)) else {
                add(g(R.string.pg_an_read, s.monthsUsed))
                if (fixedCount > 0) add(g(R.string.pg_an_bills, fixedCount))
                add(g(R.string.pg_an_cats, s.items.size))
                if (savingsItem != null) add(g(R.string.pg_an_save, money(savingsItem.average)))
            }
            add(g(if (aiAllowed) R.string.pg_an_ai else R.string.pg_an_prep))
        }

        AnimatedContent(
            targetState = scr,
            transitionSpec = {
                val forward = targetState.seq > initialState.seq
                (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320, delayMillis = 60))) togetherWith
                    (slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160))) using
                    SizeTransform(clip = false)
            },
            label = "guide_screen",
        ) { cur ->
            val sc = cur.id
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                when {
                    sc == ANALYSING -> Stages(g(R.string.pg_an_title), analysisLines, ready = useAi != null, onFinished = { go(0) })
                    sc == THINKING -> Stages(g(R.string.pg_think), listOf(g(R.string.pg_think)), ready = !asking, showTitle = false, onFinished = {})
                    sc == BUILDING -> Stages(
                        g(R.string.pg_bd_title),
                        listOf(g(R.string.pg_bd_fit, money(income)), g(R.string.pg_bd_job), g(R.string.pg_bd_done)),
                        onFinished = { go(SUMMARY) },
                    )
                    sc == SUMMARY -> {
                        val result = remember(answers) { PlanGuide.build(s, income, answers) }
                        var shownItems by remember(result) { mutableStateOf(result.items) }
                        val say: Say = { id, args -> g(id, *args.toTypedArray()) }
                        Text(g(R.string.pg_summary_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                        Text(g(R.string.pg_summary_sub, s.monthsUsed), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                        if (s.monthsUsed < 2) {
                            Spacer(Modifier.height(10.dp))
                            Text(g(R.string.pg_starter_note), fontSize = 12.sp, color = SemanticWarning)
                        }
                        if (aiNote != null) {
                            Spacer(Modifier.height(10.dp))
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(12.dp)) {
                                Text(g(R.string.pg_note_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                                Text(safeText(vis, aiNote!!), fontSize = 13.sp, color = scheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            shownItems.forEachIndexed { i, it ->
                                // rows drop in one after another, so the plan seems to be assembled in front of you
                                var shown by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { delay(70L * i); shown = true }
                                AnimatedVisibility(shown, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) {
                                    Column {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(categoryLabel(it.category), fontSize = 14.sp, color = scheme.onSurface)
                                            Text(money(it.limit), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                                        }
                                        Text(basisText(it, say), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (result.addedToSavings > 0.5) {
                            Text(g(R.string.pg_summary_extra, money(result.addedToSavings)), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        BackTestCard(
                            rows = shownItems.map { BackTest.Row(it.category, it.kind, it.limit) },
                            suggestion = s, month = month, vis = vis, say = say,
                            onRaise = { category, newLimit ->
                                val raised = BackTest.raise(shownItems.associate { it.category to it.limit }, shownItems.associate { it.category to it.kind }, category, newLimit, result.income)
                                shownItems = shownItems.map { it.copy(limit = raised[it.category] ?: it.limit) }.filter { it.limit > 0.5 }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        AppButton(g(R.string.pg_use), onClick = { onApply(result.copy(items = shownItems)) }, size = ButtonSize.Large, fullWidth = true)
                        if (useAi != true) AppButton(g(R.string.back), onClick = { go(staticQs.lastIndex, back = true) }, variant = ButtonVariant.Text, fullWidth = true)
                        if (useAi == true && aiModel != null) Text(g(R.string.pg_ai_badge, aiModel!!), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    useAi == true && sc in aiQs.indices -> {
                        val q = aiQs[sc]
                        Text(g(R.string.pg_progress_ai, sc + 1), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        if (translating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant)
                        else {
                            val progress by animateFloatAsState(((sc + 1f) / 5f).coerceAtMost(1f), tween(400), label = "guide_progress")
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        val moreBills = q.topic.equals("commitments", true) && billLog.isNotEmpty()
                        val optionIdx = q.options.indices.filter { it !in usedOptions }
                        val pendingOpt = pendingInput
                        if (pendingOpt != null) {
                            val o = q.options[pendingOpt]
                            Text(g(R.string.pg_input_for, o.effect.name ?: o.effect.category ?: o.label), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                            Spacer(Modifier.height(14.dp))
                            com.example.spendsync.ui.components.AppTextField(
                                value = inputText,
                                onValueChange = { inputText = it.filter { c -> c.isDigit() } },
                                label = g(R.string.pg_input_hint),
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppButton(g(R.string.back), onClick = { pendingInput = null }, variant = ButtonVariant.Outline, modifier = Modifier.weight(1f))
                                AppButton(g(R.string.pg_input_ok), onClick = { confirmAmount(q) }, enabled = (inputText.toDoubleOrNull() ?: 0.0) > 0, modifier = Modifier.weight(1f))
                            }
                        } else {
                            Question(
                                if (moreBills) g(R.string.pg_bills_more) else q.question,
                                picked,
                                optionIdx.map { i ->
                                    val o = q.options[i]
                                    val label = if (moreBills && o.effect.type == "none") g(R.string.pg_bills_done) else o.label
                                    safeText(vis, label) to { choose(q, i) }
                                },
                                textFilter = { safeText(vis, it) },
                            )
                        }
                        if (aiModel != null) Text(g(R.string.pg_ai_badge, aiModel!!), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                    sc in staticQs.indices -> {
                        val q = staticQs[sc]
                        Text(g(R.string.pg_progress, sc + 1, staticQs.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        val progress by animateFloatAsState((sc + 1f) / staticQs.size, tween(400), label = "guide_progress")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))
                        if (sc == 0 && !aiAllowed) Text(g(R.string.pg_need_consent), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                        if (sc == 0 && s.items.isEmpty()) Text(g(R.string.pg_no_history), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                        when (q) {
                            Q.Fixed -> Question(g(R.string.pg_q_fixed), picked, listOf(
                                g(R.string.pg_a_fixed_keep, money(PlanGuide.fixedTotal(s))) to { pickStatic(answers.copy(keepFixed = true), 0) },
                                g(R.string.pg_a_fixed_skip) to { pickStatic(answers.copy(keepFixed = false), 1) },
                            ))
                            Q.Save -> Question(g(R.string.pg_q_save), picked, listOf(
                                g(R.string.pg_a_save_none) to { pickStatic(answers.copy(savePercent = 0), 0) },
                            ) + listOf(10, 20, 30).mapIndexed { i, p ->
                                g(R.string.pg_a_save_pct, p, money(income * p / 100)) to { pickStatic(answers.copy(savePercent = p), i + 1) }
                            })
                            Q.Style -> Question(g(R.string.pg_q_style), picked, listOf(
                                g(R.string.pg_a_style_same) to { pickStatic(answers.copy(everydayChange = 0), 0) },
                                g(R.string.pg_a_style_tight) to { pickStatic(answers.copy(everydayChange = -15), 1) },
                                g(R.string.pg_a_style_tighter) to { pickStatic(answers.copy(everydayChange = -30), 2) },
                                g(R.string.pg_a_style_relaxed) to { pickStatic(answers.copy(everydayChange = 10), 3) },
                            ))
                            Q.Buffer -> Question(g(R.string.pg_q_buffer), picked, listOf(
                                g(R.string.pg_a_buffer_none) to { pickStatic(answers.copy(bufferPercent = 0), 0) },
                            ) + listOf(5, 10).mapIndexed { i, p ->
                                g(R.string.pg_a_buffer_pct, p, money(income * p / 100)) to { pickStatic(answers.copy(bufferPercent = p), i + 1) }
                            })
                        }
                        if (sc > 0) {
                            Spacer(Modifier.height(8.dp))
                            AppButton(g(R.string.back), onClick = { picked = -1; go(sc - 1, back = true) }, variant = ButtonVariant.Text)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * Lines that appear one by one, each with a spinner that turns into a tick. The last line keeps spinning until
 * [ready] (the real work behind it has finished), so what you see on screen is what is actually happening.
 */
@Composable
private fun Stages(title: String, lines: List<String>, ready: Boolean = true, showTitle: Boolean = true, onFinished: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var done by remember { mutableStateOf(0) }
    val readyNow by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        for (i in lines.indices) {
            delay(650)
            if (i == lines.lastIndex) while (!readyNow) delay(100)
            done = i + 1
        }
        delay(450)
        onFinished()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showTitle) Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        lines.forEachIndexed { i, line ->
            AnimatedVisibility(i <= done, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        if (i < done) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                        else CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.primary)
                    }
                    Text(line, fontSize = 15.sp, color = if (i < done) scheme.onSurface else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Question(text: String, picked: Int, options: List<Pair<String, () -> Unit>>, textFilter: (String) -> String = { it }) {
    val scheme = MaterialTheme.colorScheme
    Text(textFilter(text), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, (label, action) ->
            val chosen = picked == i
            val bg by animateColorAsState(if (chosen) scheme.primary.copy(alpha = 0.16f) else Color.Transparent, tween(180), label = "opt_bg")
            val scale by animateFloatAsState(if (chosen) 0.98f else 1f, tween(180), label = "opt_scale")
            Text(
                label, fontSize = 15.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                color = if (chosen) scheme.primary else scheme.onSurface,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                    .glassCard().background(bg, RoundedCornerShape(20.dp)).clickable(onClick = action).padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}
