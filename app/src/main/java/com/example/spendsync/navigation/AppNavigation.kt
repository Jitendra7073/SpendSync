package com.example.spendsync.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.auth.AuthViewModel
import com.example.spendsync.ui.auth.AuthViewModelFactory
import com.example.spendsync.ui.auth.LoginScreen
import com.example.spendsync.ui.auth.RegisterScreen
import com.example.spendsync.ui.main.MainScreen
import com.example.spendsync.ui.splash.SplashScreen
import com.example.spendsync.ui.splash.SplashViewModel
import com.example.spendsync.ui.splash.SplashViewModelFactory
import com.example.spendsync.data.repository.hydrateSettingsFromBackend
import com.example.spendsync.data.repository.warmFinanceCache
import kotlinx.coroutines.launch

// ── Top-level route constants ─────────────────────────────────────────────────

object Route {
    const val SPLASH   = "splash"
    const val LOGIN    = "login"
    const val REGISTER = "register"
    const val MAIN     = "main"   // hosts MainScreen which owns the bottom nav
}

// ── Root nav graph ────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(
    sessionDataStore: SessionDataStore,
    navController: NavHostController = rememberNavController(),
) {
    val context          = LocalContext.current
    val repository       = remember { AuthRepository(sessionDataStore) }
    val financeRepository = remember { FinanceRepository(sessionDataStore) }
    val scope            = rememberCoroutineScope()

    val splashViewModel: SplashViewModel = viewModel(
        factory = SplashViewModelFactory(repository),
    )
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(repository),
    )

    NavHost(
        navController    = navController,
        startDestination = Route.SPLASH,
        enterTransition  = {
            fadeIn(tween(300)) + slideIntoContainer(
                towards       = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300),
            )
        },
        exitTransition   = {
            fadeOut(tween(200)) + slideOutOfContainer(
                towards       = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(200),
            )
        },
        popEnterTransition = {
            fadeIn(tween(300)) + slideIntoContainer(
                towards       = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300),
            )
        },
        popExitTransition = {
            fadeOut(tween(200)) + slideOutOfContainer(
                towards       = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(200),
            )
        },
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        composable(
            route           = Route.SPLASH,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition  = { fadeOut(tween(400)) },
        ) {
            SplashScreen(
                viewModel           = splashViewModel,
                onNavigateToHome    = {
                    // Fire-and-forget — Home reads from SessionDataStore reactively,
                    // so it'll pick up synced settings as soon as this resolves.
                    scope.launch { hydrateSettingsFromBackend(financeRepository, sessionDataStore) }
                    // Warm every tab's default-view cache now, so Home/Analytics/
                    // Budget/Profile already have data by the time the user taps them.
                    scope.launch { warmFinanceCache(financeRepository) }
                    navController.navigate(Route.MAIN) {
                        // Pop SPLASH off the stack (inclusive) AFTER MAIN is pushed.
                        // This is safe because MAIN is pushed first, so the stack
                        // is never empty — it always has at least MAIN in it.
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable(route = Route.LOGIN) {
            LoginScreen(
                viewModel            = authViewModel,
                onNavigateToHome     = {
                    scope.launch { hydrateSettingsFromBackend(financeRepository, sessionDataStore) }
                    scope.launch { warmFinanceCache(financeRepository) }
                    // Clear LOGIN off the stack once signed in. Stack: [MAIN].
                    navController.navigate(Route.MAIN) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Route.REGISTER)
                },
            )
        }

        // ── Register ──────────────────────────────────────────────────────────
        composable(route = Route.REGISTER) {
            RegisterScreen(
                viewModel         = authViewModel,
                onNavigateToHome  = {
                    scope.launch { hydrateSettingsFromBackend(financeRepository, sessionDataStore) }
                    scope.launch { warmFinanceCache(financeRepository) }
                    // Clear the auth stack (LOGIN → REGISTER) once registered.
                    // Stack: [MAIN]. Never empty (MAIN pushed first).
                    navController.navigate(Route.MAIN) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigateUp()
                },
            )
        }

        // ── Main (bottom nav host) ────────────────────────────────────────────
        composable(route = Route.MAIN) {
            MainScreen(
                repository       = repository,
                financeRepository = financeRepository,
                sessionDataStore = sessionDataStore,
                onSignOut        = {
                    // Drop cached data so the next signed-in user never sees it.
                    financeRepository.clearCache()
                    // Back to LOGIN, pop MAIN off. Stack: [LOGIN].
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}
