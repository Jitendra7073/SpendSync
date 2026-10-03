package com.example.spendsync.ui.holds

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.HoldReminderWorker
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppConfirmDialog
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.shared.MonthPickerDialog
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.utils.LocalizationUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One person's holds: what they owe or are owed, when it's due, and the actions for each. */
@Composable
fun HoldDetailScreen(
    personName: String,
    holds: List<HoldDto>,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    amountVisibility: AmountVisibilityState,
    onBack: () -> Unit,
    onHoldsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val datePattern = remember(dateFormat) { LocalizationUtils.getDateFormatPattern(dateFormat) }
    val locale = Locale.getDefault()

    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var holdToEdit by remember { mutableStateOf<HoldDto?>(null) }
    var holdToDelete by remember { mutableStateOf<HoldDto?>(null) }
    var deleting by remember { mutableStateOf(false) }

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
            when (val res = financeRepository.updateHold(id = hold.id, personName = newPersonName, expectedReturnDate = "${newDate}T00:00:00.000Z")) {
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
            deleting = true
            when (val res = financeRepository.deleteHold(hold.id)) {
                is AuthResult.Success -> {
                    HoldReminderWorker.cancel(context, hold.id)
                    holdToDelete = null
                    onHoldsChanged()
                }
                is AuthResult.Error -> toast = ToastMessage(res.message, isError = true)
            }
            deleting = false
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        SettingsBackdrop {
            Column(Modifier.fillMaxSize()) {
                SettingsTopBar(personName, onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(holds, key = { it.id }) { hold ->
                            HoldCard(
                                hold = hold,
                                datePattern = datePattern,
                                locale = locale,
                                amountVisibility = amountVisibility,
                                onEdit = { holdToEdit = hold },
                                onSettle = { markSettled(hold) },
                                onDelete = { holdToDelete = hold },
                            )
                        }
                        item { Spacer(Modifier.height(110.dp)) }
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
        AppConfirmDialog(
            title = tr(R.string.delete_this_hold),
            message = tr(R.string.you_ll_stop_tracking_this_money, personName),
            confirmLabel = tr(R.string.delete),
            cancelLabel = tr(R.string.keep_it),
            destructive = true,
            loading = deleting,
            onConfirm = { deleteHold(hold) },
            onDismiss = { if (!deleting) holdToDelete = null },
        )
    }
}

@Composable
private fun HoldCard(
    hold: HoldDto,
    datePattern: String,
    locale: Locale,
    amountVisibility: AmountVisibilityState,
    onEdit: () -> Unit,
    onSettle: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val owedToYou = hold.direction == "owed_to_me"
    val tint = if (owedToYou) incomeColor() else expenseColor()
    val settled = hold.status != "pending"
    val due = remember(hold.expectedReturnDate, datePattern, locale) {
        try {
            java.time.ZonedDateTime.parse(hold.expectedReturnDate).toLocalDate().format(DateTimeFormatter.ofPattern(datePattern, locale))
        } catch (e: Exception) {
            hold.expectedReturnDate.take(10)
        }
    }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (owedToYou) tr(R.string.owes_you) else tr(R.string.you_owe),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            MaskableAmountText(hold.amount.toDoubleOrNull() ?: 0.0, amountVisibility, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr(R.string.due_and_status, due, if (settled) tr(R.string.settled) else tr(R.string.pending)),
            fontSize = 13.sp,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppButton(tr(R.string.edit), onEdit, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.Edit)
            if (!settled) AppButton(tr(R.string.mark_as_settled), onSettle, size = ButtonSize.Small, leadingIcon = Icons.Default.Handshake)
            AppButton(tr(R.string.delete), onDelete, variant = ButtonVariant.DangerText, size = ButtonSize.Small)
        }
    }
}

@Composable
private fun EditHoldDialog(
    hold: HoldDto,
    onDismiss: () -> Unit,
    onSave: (personName: String, expectedReturnDate: LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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

    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.edit_this_hold),
        message = tr(R.string.change_who_it_s_with_or),
        icon = Icons.Default.Handshake,
        primary = DialogAction(tr(R.string.save), { onSave(personName.trim(), expectedDate) }, enabled = personName.isNotBlank()),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        AppTextField(personName, { personName = it }, label = tr(R.string.person), leadingIcon = Icons.Default.Person)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.expected_return), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Text(
                    expectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            }
            AppButton(tr(R.string.change), { showDatePicker = true }, variant = ButtonVariant.Text, size = ButtonSize.Small, leadingIcon = Icons.Default.CalendarMonth)
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
