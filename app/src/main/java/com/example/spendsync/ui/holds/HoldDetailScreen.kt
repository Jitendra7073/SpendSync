package com.example.spendsync.ui.holds

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.HoldReminderWorker
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.shared.MonthPickerDialog
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldDetailScreen(
    personName: String,
    holds: List<HoldDto>,
    financeRepository: FinanceRepository,
    onBack: () -> Unit,
    onHoldsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    val SemanticError = MaterialTheme.colorScheme.error

    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var holdToEdit by remember { mutableStateOf<HoldDto?>(null) }
    var holdToDelete by remember { mutableStateOf<HoldDto?>(null) }

    fun rescheduleReminder(hold: HoldDto, newPersonName: String, newDate: LocalDate) {
        HoldReminderWorker.cancel(context, hold.id)
        if (hold.status == "pending") {
            HoldReminderWorker.schedule(
                context = context,
                holdId = hold.id,
                personName = newPersonName,
                amount = hold.amount.toDoubleOrNull() ?: 0.0,
                direction = hold.direction,
                expectedReturnDate = newDate,
            )
        }
    }

    fun markSettled(hold: HoldDto) {
        scope.launch {
            when (val res = financeRepository.updateHold(id = hold.id, status = "settled")) {
                is AuthResult.Success -> {
                    HoldReminderWorker.cancel(context, hold.id)
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }

    fun saveEdit(hold: HoldDto, newPersonName: String, newDate: LocalDate) {
        scope.launch {
            val isoDate = "${newDate}T00:00:00.000Z"
            when (val res = financeRepository.updateHold(
                id = hold.id,
                personName = newPersonName,
                expectedReturnDate = isoDate,
            )) {
                is AuthResult.Success -> {
                    rescheduleReminder(hold, newPersonName, newDate)
                    holdToEdit = null
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }

    fun deleteHold(hold: HoldDto) {
        scope.launch {
            when (val res = financeRepository.deleteHold(hold.id)) {
                is AuthResult.Success -> {
                    HoldReminderWorker.cancel(context, hold.id)
                    holdToDelete = null
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        Column(modifier = Modifier.fillMaxSize().background(NeutralOffWhite)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
                }
                Spacer(Modifier.width(8.dp))
                Text(personName, fontSize = 20.sp, color = NeutralBlack)
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
                                Text(
                                    text = if (hold.direction == "owed_to_me") "Owed to you" else "You owe",
                                    fontSize = 13.sp,
                                    color = NeutralMid,
                                )
                                Text(
                                    formatInr(hold.amount.toDoubleOrNull() ?: 0.0),
                                    fontSize = 16.sp,
                                    color = NeutralBlack,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Expected: ${hold.expectedReturnDate.take(10)} · ${hold.status}",
                                fontSize = 12.sp,
                                color = NeutralMid,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { holdToEdit = hold }) {
                                    Text("Edit")
                                }
                                if (hold.status == "pending") {
                                    Button(
                                        onClick = { markSettled(hold) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                    ) {
                                        Text("Mark as settled")
                                    }
                                }
                                TextButton(onClick = { holdToDelete = hold }) {
                                    Text("Delete", color = SemanticError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    holdToEdit?.let { hold ->
        EditHoldDialog(
            hold = hold,
            onDismiss = { holdToEdit = null },
            onSave = { newPersonName, newDate -> saveEdit(hold, newPersonName, newDate) },
        )
    }

    holdToDelete?.let { hold ->
        BasicAlertDialog(
            onDismissRequest = { holdToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Delete this hold?", fontSize = 18.sp, color = NeutralBlack)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This removes tracking for ${formatInr(hold.amount.toDoubleOrNull() ?: 0.0)} with $personName. This can't be undone.",
                        fontSize = 13.sp,
                        color = NeutralMid,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { holdToDelete = null }) { Text("Cancel") }
                        TextButton(onClick = { deleteHold(hold) }) { Text("Delete", color = SemanticError) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHoldDialog(
    hold: HoldDto,
    onDismiss: () -> Unit,
    onSave: (personName: String, expectedReturnDate: LocalDate) -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val BrandBlue = MaterialTheme.colorScheme.primary

    var personName by remember { mutableStateOf(hold.personName) }
    var expectedDate by remember {
        mutableStateOf(
            try {
                java.time.ZonedDateTime.parse(hold.expectedReturnDate).toLocalDate()
            } catch (e: Exception) {
                LocalDate.now()
            }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Edit hold", fontSize = 18.sp, color = NeutralBlack)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = NeutralLight,
                        cursorColor = BrandBlue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeutralLight.copy(alpha = 0.3f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Expected return: $expectedDate",
                        color = NeutralBlack,
                    )
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text("Change date")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = { onSave(personName, expectedDate) },
                        enabled = personName.isNotBlank(),
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showDatePicker) {
        MonthPickerDialog(
            current = expectedDate,
            onConfirm = { picked -> expectedDate = picked; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}
