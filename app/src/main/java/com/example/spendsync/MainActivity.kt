package com.example.spendsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.navigation.AppNavigation
import com.example.spendsync.navigation.Route
import com.example.spendsync.ui.theme.SpendSyncTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val sessionDataStore = SessionDataStore(applicationContext)
        val authRepository = AuthRepository(sessionDataStore)

        // No branded splash UI — the system splash (plain background, no
        // logo) stays up for this one quick local-session check, then we
        // land directly on Home or Login. Nothing in between to "remove".
        var startDestination by mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            startDestination = if (authRepository.hasLocalSession()) Route.MAIN else Route.LOGIN
        }

        setContent {
            val darkMode by sessionDataStore.darkMode.collectAsState(initial = false)
            val accentColor by sessionDataStore.accentColor.collectAsState(initial = "Brand Blue")
            val resolvedStart = startDestination

            SpendSyncTheme(
                darkTheme = darkMode,
                accentColorName = accentColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (resolvedStart != null) {
                        AppNavigation(
                            sessionDataStore = sessionDataStore,
                            authRepository   = authRepository,
                            startDestination = resolvedStart,
                        )
                    }
                }
            }
        }
    }
}
