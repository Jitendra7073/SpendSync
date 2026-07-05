package com.example.spendsync.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.remote.model.DashboardSummaryDto
import androidx.compose.runtime.LaunchedEffect
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandBlueDark
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class ProfileRoute { PROFILE, SETTINGS }

@Composable
fun ProfileScreen(
    sessionDataStore: SessionDataStore,
    repository: AuthRepository,
    financeRepository: FinanceRepository,
    onSignOut: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(ProfileRoute.PROFILE) }

    AnimatedContent(
        targetState   = route,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label          = "profile_route",
    ) { current ->
        when (current) {
            ProfileRoute.PROFILE  -> ProfileContent(
                sessionDataStore  = sessionDataStore,
                repository        = repository,
                financeRepository = financeRepository,
                onOpenSettings    = { route = ProfileRoute.SETTINGS },
                onSignOut         = onSignOut,
            )
            ProfileRoute.SETTINGS -> SettingsScreen(
                sessionDataStore = sessionDataStore,
                onBack = { route = ProfileRoute.PROFILE },
            )
        }
    }
}

@Composable
private fun ProfileContent(
    sessionDataStore: SessionDataStore,
    repository: AuthRepository,
    financeRepository: FinanceRepository,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant

    val userName  by sessionDataStore.userName.collectAsState(initial = "")
    val userEmail by sessionDataStore.userEmail.collectAsState(initial = "")
    val userId    by sessionDataStore.userId.collectAsState(initial = "")
    val userCreatedAt by sessionDataStore.userCreatedAt.collectAsState(initial = "")
    val scope     = rememberCoroutineScope()

    val today = remember { LocalDate.now() }

    var dashboardSummary by remember { mutableStateOf<DashboardSummaryDto?>(null) }
    LaunchedEffect(Unit) {
        val activeMonth = LocalDate.now().toString().slice(0..6)
        when (val res = financeRepository.getDashboardSummary(month = activeMonth)) {
            is AuthResult.Success -> {
                dashboardSummary = res.data
            }
            is AuthResult.Error -> {
                // handle error
            }
        }
    }

    // Dialog state controllers
    var showEditProfile by remember { mutableStateOf(false) }
    var showAccountDetails by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Blue header band ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandBlue, BrandBlueDark),
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar circle with initials
                Box(
                    modifier         = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(NeutralWhite.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = (userName?.firstOrNull() ?: "?")
                            .toString().uppercase(),
                        color      = NeutralWhite,
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text       = userName?.ifBlank { "Guest" } ?: "Guest",
                    color      = NeutralWhite,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = userEmail ?: "",
                    color    = NeutralWhite.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                )

                Spacer(Modifier.height(16.dp))

                // Yellow accent bar
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BrandYellow),
                )

                Spacer(Modifier.height(16.dp))

                // Edit profile chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NeutralWhite.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true, color = NeutralWhite),
                            onClick           = { showEditProfile = true },
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint               = NeutralWhite,
                            modifier           = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text      = "Edit Profile",
                            color     = NeutralWhite,
                            fontSize  = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ── Dynamic stats strip ───────────────────────────────────────────────
        val totalTransactionsCount = dashboardSummary?.totals?.totalTransactions ?: 0
        val currentMonthSpent = dashboardSummary?.totals?.totalSpent ?: 0.0
        val savingsAccumulated = dashboardSummary?.totals?.netAmount ?: 0.0

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralWhite)
                .padding(vertical = 16.dp),
        ) {
            StatItem(
                label  = "Transactions",
                value  = totalTransactionsCount.toString(),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(NeutralLight)
                    .align(Alignment.CenterVertically),
            )
            StatItem(
                label  = "This Month",
                value  = "$%,.2f".format(currentMonthSpent),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(NeutralLight)
                    .align(Alignment.CenterVertically),
            )
            StatItem(
                label  = "Savings",
                value  = "$%,.2f".format(savingsAccumulated),
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = NeutralLight, thickness = 1.dp)

        // ── Menu list ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            ProfileMenuCard {
                ProfileMenuItem(
                    icon    = Icons.Default.AccountCircle,
                    label   = "Account",
                    sub     = "Manage your personal information",
                    onClick = { showAccountDetails = true },
                )
                MenuDivider()
                ProfileMenuItem(
                    icon    = Icons.Default.Settings,
                    label   = "Settings",
                    sub     = "Preferences, appearance & security",
                    onClick = onOpenSettings,
                )
                MenuDivider()
                ProfileMenuItem(
                    icon    = Icons.AutoMirrored.Filled.HelpOutline,
                    label   = "Support",
                    sub     = "Help centre, contact us, FAQs",
                    onClick = { showSupport = true },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Logout standalone card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeutralWhite),
            ) {
                ProfileMenuItem(
                    icon      = Icons.AutoMirrored.Filled.Logout,
                    label     = "Sign Out",
                    sub       = "You can always sign back in",
                    iconTint  = SemanticError,
                    textColor = SemanticError,
                    onClick   = {
                        scope.launch {
                            repository.signOut()
                            onSignOut()
                        }
                    },
                    showChevron = false,
                )
            }

            Spacer(Modifier.height(100.dp))
        }
    }

    // ── Dialog 1: Edit Profile Dialog ────────────────────────────────────────
    if (showEditProfile) {
        EditProfileDialog(
            currentName = userName ?: "",
            currentEmail = userEmail ?: "",
            onDismiss = { showEditProfile = false },
            onSave = { newName, newEmail ->
                scope.launch {
                    sessionDataStore.updateUserName(newName)
                    sessionDataStore.updateUserEmail(newEmail)
                    showEditProfile = false
                }
            }
        )
    }

    // ── Dialog 2: Account Details Info Dialog ───────────────────────────────
    if (showAccountDetails) {
        AccountDetailsDialog(
            userId = userId ?: "",
            name = userName ?: "Guest",
            email = userEmail ?: "guest@example.com",
            createdAt = userCreatedAt ?: "",
            onDismiss = { showAccountDetails = false }
        )
    }

    // ── Dialog 3: Support Center FAQ Dialog ─────────────────────────────────
    if (showSupport) {
        SupportCenterDialog(
            onDismiss = { showSupport = false }
        )
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────────

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = value,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = NeutralBlack,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text     = label,
            fontSize = 11.sp,
            color    = NeutralMid,
        )
    }
}

@Composable
private fun ProfileMenuCard(content: @Composable () -> Unit) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NeutralWhite),
    ) {
        content()
    }
}

@Composable
private fun MenuDivider() {
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        color     = NeutralLight,
        thickness = 0.8.dp,
    )
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    iconTint: Color = BrandBlue,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = iconTint,
                modifier           = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = textColor,
            )
            if (sub != null) {
                Text(text = sub, fontSize = 12.sp, color = NeutralMid)
            }
        }
        if (showChevron) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint               = NeutralMid,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

// ── Dialog Components ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Edit Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )
                
                Spacer(Modifier.height(16.dp))

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = NeutralLight,
                        cursorColor = BrandBlue,
                        focusedLabelColor = BrandBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = NeutralLight,
                        cursorColor = BrandBlue,
                        focusedLabelColor = BrandBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Cancel",
                        color = NeutralMid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Save Changes",
                        color = NeutralWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandBlue)
                            .clickable(enabled = name.isNotBlank() && email.isNotBlank()) {
                                onSave(name, email)
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailsDialog(
    userId: String,
    name: String,
    email: String,
    createdAt: String,
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Account Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralBlack
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralMid)
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                val formattedJoinedDate = remember(createdAt) {
                    try {
                        val parsed = java.time.ZonedDateTime.parse(createdAt)
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy")
                        parsed.format(formatter)
                    } catch (e: Exception) {
                        if (createdAt.isNullOrBlank()) "Just now" else createdAt
                    }
                }

                AccountInfoRow(label = "User ID", value = userId.ifBlank { "Guest Mode" })
                AccountInfoRow(label = "Profile Name", value = name)
                AccountInfoRow(label = "Email", value = email)
                AccountInfoRow(label = "Joined SpendSync", value = formattedJoinedDate)
                AccountInfoRow(label = "Subscription Tier", value = if (userId.isNotBlank()) "Premium Account" else "Free Basic Plan")
                AccountInfoRow(label = "Cloud Sync Status", value = if (userId.isNotBlank()) "Active / Secured" else "Offline / Not Synced")

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = NeutralMid, fontSize = 13.sp)
        Text(text = value, color = NeutralBlack, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportCenterDialog(
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .height(380.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Support & FAQs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralBlack
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralMid)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FAQItem(
                        question = "How do I back up my transactions?",
                        answer = "Auto Backup is enabled by default in Settings. Your data is synced automatically to our secure cloud daily."
                    )
                    FAQItem(
                        question = "Can I change my default currency?",
                        answer = "Yes. Go to Profile > Settings > Personalisation > Currency to choose USD, EUR, GBP, or INR."
                    )
                    FAQItem(
                        question = "Is my financial data secure?",
                        answer = "Absolutely. We encrypt all transactions on-device and transit data to ensure your info stays private."
                    )
                    FAQItem(
                        question = "How to delete my account permanently?",
                        answer = "Go to Settings > Data > Delete Account. This wipes all your data permanently off our cloud databases."
                    )
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandBlue)
                        .clickable { /* Simulate email support launch */ }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Contact Email Support",
                        color = NeutralWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FAQItem(question: String, answer: String) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeutralOffWhite)
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Text(
            text = question,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = NeutralBlack
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = answer,
                fontSize = 12.sp,
                color = NeutralMid
            )
        }
    }
}
