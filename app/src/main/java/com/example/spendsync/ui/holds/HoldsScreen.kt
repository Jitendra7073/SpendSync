package com.example.spendsync.ui.holds

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.HoldReminderWorker
import com.example.spendsync.utils.formatInr
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun HoldsScreen(
    financeRepository: FinanceRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary

    var holds by remember { mutableStateOf<List<HoldDto>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        when (val res = financeRepository.getHolds()) {
            is AuthResult.Success -> holds = res.data
            is AuthResult.Error -> Unit
        }
    }

    fun markSettled(hold: HoldDto) {
        scope.launch {
            val res = financeRepository.updateHold(id = hold.id, status = "settled")
            if (res is AuthResult.Success) {
                HoldReminderWorker.cancel(context, hold.id)
                refreshKey++
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(NeutralOffWhite)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
            }
            Spacer(Modifier.height(0.dp))
            Text("Holds", fontSize = 20.sp, color = NeutralBlack, modifier = Modifier.padding(start = 8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(holds) { hold ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(hold.personName, fontSize = 16.sp, color = NeutralBlack)
                            Text(formatInr(hold.amount.toDoubleOrNull() ?: 0.0), fontSize = 16.sp, color = NeutralBlack)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (hold.direction == "owed_to_me") "Owed to you" else "You owe",
                            fontSize = 12.sp,
                            color = NeutralMid,
                        )
                        Text(
                            text = "Expected: ${hold.expectedReturnDate.take(10)} · ${hold.status}",
                            fontSize = 12.sp,
                            color = NeutralMid,
                        )
                        if (hold.status == "pending") {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { markSettled(hold) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            ) {
                                Text("Mark as settled")
                            }
                        }
                    }
                }
            }
        }
    }
}
