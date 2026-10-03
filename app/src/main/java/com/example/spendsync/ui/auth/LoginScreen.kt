package com.example.spendsync.ui.auth

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import com.example.spendsync.ui.components.Text
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
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow

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
        // A flexible Column (header sized to its own content, card takes the
        // rest via weight) replaces a hard 0.42f/0.68f height split so short
        // or landscape screens — or the keyboard opening — never clip content;
        // the card's own verticalScroll absorbs any remaining overflow.
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Blue header ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandBlue)
                    .statusBarsPadding()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text          = "SpendSync",
                        color         = MaterialTheme.colorScheme.onPrimary,
                        fontSize      = 28.sp,
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
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(NeutralWhite),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text       = tr(R.string.welcome_back_2),
                        color      = BrandBlue,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = tr(R.string.login_to_your_spendsync_account),
                        color    = NeutralMid,
                        fontSize = 12.sp,
                    )

                    Spacer(Modifier.height(28.dp))

                    // Email
                    AuthTextField(
                        value           = uiState.email,
                        onValueChange   = viewModel::onEmailChanged,
                        label           = tr(R.string.email_address),
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
                        label                   = tr(R.string.password),
                        leadingIcon             = Icons.Default.Lock,
                        isPassword              = true,
                        passwordVisible         = uiState.passwordVisible,
                        trailingIcon            = if (uiState.passwordVisible)
                            Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        onTrailingIconClick     = viewModel::togglePasswordVisibility,
                        trailingIconDescription = if (uiState.passwordVisible)
                            tr(R.string.hide_password) else tr(R.string.show_password),
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


                    Spacer(Modifier.height(20.dp))

                    // Primary action
                    AppButton(
                        text = tr(R.string.log_in),
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.signIn()
                        },
                        size = ButtonSize.Large,
                        loading = uiState.isLoading,
                        enabled = !uiState.isLoading,
                        fullWidth = true,
                    )

                    Spacer(Modifier.height(28.dp))

                    // Sign-up link
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = tr(R.string.don_t_have_an_account),
                            color    = NeutralDark,
                            fontSize = 14.sp,
                        )
                        AppButton(
                            text = tr(R.string.sign_up),
                            onClick = { viewModel.navigateToRegister() },
                            variant = ButtonVariant.Text,
                            size = ButtonSize.Small,
                        )
                    }


                }
            }
        }
    }
}
