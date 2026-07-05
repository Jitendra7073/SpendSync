package com.example.spendsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.navigation.AppNavigation
import com.example.spendsync.ui.theme.SpendSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val sessionDataStore = SessionDataStore(applicationContext)

        setContent {
            val darkMode by sessionDataStore.darkMode.collectAsState(initial = false)
            val accentColor by sessionDataStore.accentColor.collectAsState(initial = "Brand Blue")

            SpendSyncTheme(
                darkTheme = darkMode,
                accentColorName = accentColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(sessionDataStore = sessionDataStore)
                }
            }
        }
    }
}
