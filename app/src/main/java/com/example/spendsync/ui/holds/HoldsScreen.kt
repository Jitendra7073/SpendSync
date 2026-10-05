package com.example.spendsync.ui.holds

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.home.HomeEmptyState
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor

private val PlaceholderPeople = List(4) { PersonHoldSummary("Person name", 2500.0, 2) }

/** Who owes you / who you owe, one card per person. Tap a person for their individual holds. */
@Composable
fun HoldsScreen(
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    amountVisibility: AmountVisibilityState,
    onBack: () -> Unit,
) {
    var holds by remember { mutableStateOf<List<HoldDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }

    // Back from a person's holds returns to the list; from the list it closes Holds. Never leaves the app.
    BackHandler { if (selectedPerson != null) selectedPerson = null else onBack() }

    LaunchedEffect(refreshKey) {
        when (val res = financeRepository.getHolds()) {
            is AuthResult.Success -> holds = res.data
            is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
        }
        loading = false
    }

    val person = selectedPerson
    if (person != null) {
        HoldDetailScreen(
            personName = person,
            holds = holds.filter { it.personName == person },
            financeRepository = financeRepository,
            sessionDataStore = sessionDataStore,
            amountVisibility = amountVisibility,
            onBack = { selectedPerson = null },
            onHoldsChanged = { refreshKey++ },
        )
        return
    }

    val summaries = remember(holds) { groupHoldsByPerson(holds) }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        SettingsBackdrop {
            Column(Modifier.fillMaxSize()) {
                SettingsTopBar(tr(R.string.holds), onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Spacer(Modifier.height(4.dp)) }
                        if (loading) {
                            item { Skeleton(true) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PlaceholderPeople.forEach { PersonCard(it, amountVisibility) {} } } } }
                        } else if (summaries.isEmpty()) {
                            item { HomeEmptyState(tr(R.string.no_holds_yet), tr(R.string.when_you_lend_or_borrow_money)) }
                        } else {
                            items(summaries, key = { it.personName }) { p -> PersonCard(p, amountVisibility) { selectedPerson = p.personName } }
                        }
                        item { Spacer(Modifier.height(110.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: PersonHoldSummary, amountVisibility: AmountVisibilityState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val owedToYou = person.netAmount >= 0
    val tint = if (owedToYou) incomeColor() else expenseColor()
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassCard()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text((person.personName.firstOrNull() ?: '?').toString().uppercase(), color = tint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(person.personName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1)
            Text(
                if (person.holdCount == 1) tr(R.string.s_1_hold) else tr(R.string.s_1_holds, person.holdCount),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (owedToYou) tr(R.string.owes_you) else tr(R.string.you_owe), fontSize = 11.sp, color = scheme.onSurfaceVariant)
            MaskableAmountText(kotlin.math.abs(person.netAmount), amountVisibility, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.size(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
    }
}
