package com.example.spendsync.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.MainActivity
import com.example.spendsync.ui.components.PrimaryButton
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.SpendSyncTheme

/**
 * Shown by [CrashHandler] after an uncaught exception. Runs in its own task so
 * it survives the death of the crashed process. Offers the user a clean restart
 * back into [MainActivity].
 */
class ErrorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent.getStringExtra(EXTRA_MESSAGE)

        setContent {
            SpendSyncTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErrorScreen(
                        detail    = message,
                        onRestart = ::restartApp,
                    )
                }
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_MESSAGE = "extra_error_message"
    }
}

@Composable
private fun ErrorScreen(
    detail: String?,
    onRestart: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint               = BrandBlue,
            modifier           = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text       = "Something went wrong",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = BrandBlue,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text      = "The app ran into an unexpected problem. Your data is safe — " +
                "just restart to continue.",
            fontSize  = 14.sp,
            color     = NeutralMid,
            textAlign = TextAlign.Center,
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text      = detail,
                fontSize  = 11.sp,
                color     = NeutralMid.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            text    = "Restart",
            onClick = onRestart,
        )
    }
}
