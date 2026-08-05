package com.example.spendsync.ui.holds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText

@Composable
fun HoldsScreen(
    financeRepository: FinanceRepository,
    amountVisibility: AmountVisibilityState,
    onBack: () -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant

    var holds by remember { mutableStateOf<List<HoldDto>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        when (val res = financeRepository.getHolds()) {
            is AuthResult.Success -> holds = res.data
            is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
        }
    }

    val currentSelectedPerson = selectedPerson
    if (currentSelectedPerson != null) {
        HoldDetailScreen(
            personName = currentSelectedPerson,
            holds = holds.filter { it.personName == currentSelectedPerson },
            financeRepository = financeRepository,
            amountVisibility = amountVisibility,
            onBack = { selectedPerson = null },
            onHoldsChanged = { refreshKey++ },
        )
        return
    }

    val personSummaries = remember(holds) { groupHoldsByPerson(holds) }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        Column(modifier = Modifier.fillMaxSize().background(NeutralOffWhite)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
                }
                Spacer(Modifier.width(8.dp))
                Text("Holds", fontSize = 20.sp, color = NeutralBlack)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(personSummaries) { person ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { selectedPerson = person.personName },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(person.personName, fontSize = 16.sp, color = NeutralBlack)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${person.holdCount} hold${if (person.holdCount == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = NeutralMid,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (person.netAmount >= 0) "Owed to you" else "You owe",
                                        fontSize = 11.sp,
                                        color = NeutralMid,
                                    )
                                    MaskableAmountText(
                                        amount = kotlin.math.abs(person.netAmount),
                                        visibility = amountVisibility,
                                        fontSize = 16.sp,
                                        color = NeutralBlack,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = NeutralMid,
                                    modifier = Modifier.padding(2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
