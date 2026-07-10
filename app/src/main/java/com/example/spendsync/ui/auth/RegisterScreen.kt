package com.example.spendsync.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.components.AuthTextField
import com.example.spendsync.ui.components.PrimaryButton
import com.example.spendsync.ui.components.TextLinkButton
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralDark
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralWhite

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralDark = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    val uiState      by viewModel.uiState.collectAsState()
    var toast        by remember { mutableStateOf<ToastMessage?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.ShowToast       -> toast = ToastMessage(event.message, event.isError)
                is AuthEvent.NavigateToHome  -> onNavigateToHome()
                is AuthEvent.NavigateToLogin -> onNavigateToLogin()
                else                         -> Unit
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        // Flexible Column (header wraps its content, card takes the rest via
        // weight) instead of a hard 0.32f/0.76f height split — Register has
        // more fields than Login, so a fixed fraction is even more likely to
        // squeeze the card on short/landscape screens or under the keyboard.
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Blue header ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandBlue)
                    .statusBarsPadding()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "SpendSync",
                        color      = NeutralWhite,
                        fontSize   = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 160.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BrandYellow),
                    )
                }
            }

            // ── White card ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(NeutralWhite),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text       = "CREATE ACCOUNT",
                        color      = BrandBlue,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = "Sign up and start tracking your expenses",
                        color    = NeutralMid,
                        fontSize = 13.sp,
                    )

                    Spacer(Modifier.height(24.dp))

                    // Full name
                    AuthTextField(
                        value           = uiState.name,
                        onValueChange   = viewModel::onNameChanged,
                        label           = "Full Name",
                        leadingIcon     = Icons.Default.Person,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        enabled = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Email
                    AuthTextField(
                        value           = uiState.email,
                        onValueChange   = viewModel::onEmailChanged,
                        label           = "Email Address",
                        leadingIcon     = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        enabled = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Password
                    AuthTextField(
                        value                   = uiState.password,
                        onValueChange           = viewModel::onPasswordChanged,
                        label                   = "Password",
                        leadingIcon             = Icons.Default.Lock,
                        isPassword              = true,
                        passwordVisible         = uiState.passwordVisible,
                        trailingIcon            = if (uiState.passwordVisible)
                            Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        onTrailingIconClick     = viewModel::togglePasswordVisibility,
                        trailingIconDescription = if (uiState.passwordVisible)
                            "Hide password" else "Show password",
                        keyboardOptions         = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Next,
                        ),
                        keyboardActions         = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        enabled = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Confirm password
                    AuthTextField(
                        value                   = uiState.confirmPassword,
                        onValueChange           = viewModel::onConfirmPasswordChanged,
                        label                   = "Confirm Password",
                        leadingIcon             = Icons.Default.Lock,
                        isPassword              = true,
                        passwordVisible         = uiState.confirmPasswordVisible,
                        trailingIcon            = if (uiState.confirmPasswordVisible)
                            Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        onTrailingIconClick     = viewModel::toggleConfirmPasswordVisibility,
                        trailingIconDescription = if (uiState.confirmPasswordVisible)
                            "Hide confirm password" else "Show confirm password",
                        keyboardOptions         = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done,
                        ),
                        keyboardActions         = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.signUp()
                            }
                        ),
                        enabled = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(28.dp))

                    // Primary action
                    PrimaryButton(
                        text      = "Create Account",
                        onClick   = {
                            focusManager.clearFocus()
                            viewModel.signUp()
                        },
                        isLoading = uiState.isLoading,
                        enabled   = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Log-in link
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = "Already have an account?",
                            color    = NeutralDark,
                            fontSize = 14.sp,
                        )
                        TextLinkButton(
                            text    = "Log In",
                            onClick = { viewModel.navigateToLogin() },
                            color   = BrandBlue,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = NeutralLight, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))

                    // Overview link — bypasses auth and goes straight to app
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = "Just browsing?",
                            color    = NeutralMid,
                            fontSize = 13.sp,
                        )
                        TextLinkButton(
                            text       = "Overview",
                            onClick    = onNavigateToHome,
                            color      = NeutralDark,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 13,
                        )
                    }
                }
            }
        }
    }
}
