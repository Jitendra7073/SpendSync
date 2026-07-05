package com.example.spendsync.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
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
                is AuthEvent.ShowToast          -> toast = ToastMessage(event.message, event.isError)
                is AuthEvent.NavigateToHome     -> onNavigateToHome()
                is AuthEvent.NavigateToRegister -> onNavigateToRegister()
                else                            -> Unit
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Blue header ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
                    .background(BrandBlue)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text          = "SpendSync",
                        color         = NeutralWhite,
                        fontSize      = 36.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 180.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BrandYellow),
                    )
                }
            }

            // ── White card ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(0.68f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(NeutralWhite),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text       = "WELCOME BACK",
                        color      = BrandBlue,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = "Login to your SpendSync account",
                        color    = NeutralMid,
                        fontSize = 13.sp,
                    )

                    Spacer(Modifier.height(28.dp))

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

                    Spacer(Modifier.height(14.dp))

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
                            imeAction    = ImeAction.Done,
                        ),
                        keyboardActions         = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.signIn()
                            }
                        ),
                        enabled = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Forgot password — proper ripple via TextLinkButton
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextLinkButton(
                            text       = "Forgot Password?",
                            onClick    = { /* TODO */ },
                            color      = BrandBlue,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 12,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Primary action
                    PrimaryButton(
                        text      = "Log In",
                        onClick   = {
                            focusManager.clearFocus()
                            viewModel.signIn()
                        },
                        isLoading = uiState.isLoading,
                        enabled   = !uiState.isLoading,
                    )

                    Spacer(Modifier.height(28.dp))

                    // Sign-up link
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = "Don't have an account?",
                            color    = NeutralDark,
                            fontSize = 14.sp,
                        )
                        TextLinkButton(
                            text    = "Sign Up",
                            onClick = { viewModel.navigateToRegister() },
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
