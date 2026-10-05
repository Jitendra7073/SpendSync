package com.example.spendsync.ui.planify

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.planify.PlanGuide
import com.example.spendsync.data.remote.model.SuggestionDto
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
import com.example.spendsync.utils.formatInr

private enum class Q { Fixed, Save, Style, Buffer }

/**
 * A few multiple-choice questions that build the month's buckets from the user's own history.
 * The numbers are computed ([PlanGuide]), the wording is fixed and translated, so nothing is made up.
 * The language menu only changes the wording in this sheet, so someone who can't read the app's language
 * can still follow the questions.
 */
@Composable
internal fun PlanGuideSheet(
    suggestion: SuggestionDto?,
    income: Double,
    vis: AmountVisibilityState,
    onApply: (PlanGuide.Result) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var language by remember { mutableStateOf(LanguageManager.current) }
    var menu by remember { mutableStateOf(false) }
    val s = suggestion ?: SuggestionDto(emptyList(), 0.0, 0)
    val questions = remember(s) {
        buildList {
            if (PlanGuide.hasFixed(s)) add(Q.Fixed)
            add(Q.Save)
            if (PlanGuide.hasEveryday(s)) add(Q.Style)
            add(Q.Buffer)
        }
    }
    var index by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(PlanGuide.Answers()) }
    fun g(@StringRes id: Int, vararg args: Any) = LanguageManager.stringIn(language, id, *args)
    fun money(v: Double) = safeText(vis, formatInr(v))
    fun pick(next: PlanGuide.Answers) { answers = next; index++ }

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

        when {
            income <= 0.0 -> {
                Text(g(R.string.pg_no_income), fontSize = 15.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                AppButton(g(R.string.got_it), onClick = onDismiss, size = ButtonSize.Large, fullWidth = true)
            }
            index < questions.size -> {
                val q = questions[index]
                Text(g(R.string.pg_progress, index + 1, questions.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { (index + 1f) / questions.size }, modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                if (index == 0 && s.items.isEmpty()) {
                    Text(g(R.string.pg_no_history), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                }
                when (q) {
                    Q.Fixed -> Question(g(R.string.pg_q_fixed), listOf(
                        g(R.string.pg_a_fixed_keep, money(PlanGuide.fixedTotal(s))) to { pick(answers.copy(keepFixed = true)) },
                        g(R.string.pg_a_fixed_skip) to { pick(answers.copy(keepFixed = false)) },
                    ))
                    Q.Save -> Question(g(R.string.pg_q_save), listOf(
                        g(R.string.pg_a_save_none) to { pick(answers.copy(savePercent = 0)) },
                    ) + listOf(10, 20, 30).map { p ->
                        g(R.string.pg_a_save_pct, p, money(income * p / 100)) to { pick(answers.copy(savePercent = p)) }
                    })
                    Q.Style -> Question(g(R.string.pg_q_style), listOf(
                        g(R.string.pg_a_style_same) to { pick(answers.copy(everydayChange = 0)) },
                        g(R.string.pg_a_style_tight) to { pick(answers.copy(everydayChange = -15)) },
                        g(R.string.pg_a_style_tighter) to { pick(answers.copy(everydayChange = -30)) },
                        g(R.string.pg_a_style_relaxed) to { pick(answers.copy(everydayChange = 10)) },
                    ))
                    Q.Buffer -> Question(g(R.string.pg_q_buffer), listOf(
                        g(R.string.pg_a_buffer_none) to { pick(answers.copy(bufferPercent = 0)) },
                    ) + listOf(5, 10).map { p ->
                        g(R.string.pg_a_buffer_pct, p, money(income * p / 100)) to { pick(answers.copy(bufferPercent = p)) }
                    })
                }
                if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                    AppButton(g(R.string.back), onClick = { index-- }, variant = ButtonVariant.Text)
                }
            }
            else -> {
                val result = remember(answers) { PlanGuide.build(s, income, answers) }
                Text(g(R.string.pg_summary_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Text(g(R.string.pg_summary_sub, s.monthsUsed), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.items.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(categoryLabel(it.category), fontSize = 14.sp, color = scheme.onSurface)
                            Text(money(it.limit), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        }
                    }
                }
                if (result.addedToSavings > 0.5) {
                    Text(g(R.string.pg_summary_extra, money(result.addedToSavings)), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(16.dp))
                AppButton(g(R.string.pg_use), onClick = { onApply(result) }, size = ButtonSize.Large, fullWidth = true)
                AppButton(g(R.string.back), onClick = { index-- }, variant = ButtonVariant.Text, fullWidth = true)
            }
        }
    }
}

@Composable
private fun Question(text: String, options: List<Pair<String, () -> Unit>>) {
    val scheme = MaterialTheme.colorScheme
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (label, action) ->
            Text(
                label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().glassCard().clickable(onClick = action).padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}
