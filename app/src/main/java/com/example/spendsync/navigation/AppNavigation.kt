package com.example.spendsync.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.AuthEvents
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.auth.AuthViewModel
import com.example.spendsync.ui.auth.AuthViewModelFactory
import com.example.spendsync.ui.auth.LoginScreen
import com.example.spendsync.ui.auth.RegisterScreen
import com.example.spendsync.ui.main.MainScreen
import com.example.spendsync.data.repository.hydrateSettingsFromBackend
import com.example.spendsync.data.repository.warmFinanceCache
import kotlinx.coroutines.launch

// ── Top-level route constants ─────────────────────────────────────────────────

object Route {
    const val LOGIN    = "login"
    const val REGISTER = "register"
    const val MAIN     = "main"   // hosts MainScreen which owns the bottom nav
}

// ── Root nav graph ────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(
    sessionDataStore: SessionDataStore,
    authRepository: AuthRepository,
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    val context          = LocalContext.current
    val repository       = authRepository
    val financeRepository = remember { FinanceRepository(sessionDataStore) }
    val scope            = rememberCoroutineScope()

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(repository),
    )

    // Already-authenticated cold start (startDestination == MAIN) skips the
    // Login/Register screens' own onNavigateToHome warmup entirely, so do it
    // once here instead.
    LaunchedEffect(Unit) {
        if (startDestination == Route.MAIN) {
            scope.launch { hydrateSettingsFromBackend(financeRepository, sessionDataStore) }
            scope.launch { warmFinanceCache(financeRepository) }
        }
    }

    // Listen for 401 Unauthorized events from AuthInterceptor.
    // Clear session, cache, and navigate to Login with full back stack clear.
    LaunchedEffect(Unit) {
        AuthEvents.unauthorizedFlow.collect {
            sessionDataStore.clearSession()
            financeRepository.clearCache()
            navController.navigate(Route.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }


    // Guard against returning to the app with an expired/cleared local session.
    // On every ON_RESUME, check whether a local token still exists — if not,
    // redirect to Login (unless we're already on an auth screen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != Route.LOGIN && currentRoute != Route.REGISTER) {
                    scope.launch {
                        if (!authRepository.hasLocalSession()) {
                            navController.navigate(Route.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
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
                repository        = repository,
                financeRepository = financeRepository,
                sessionDataStore  = sessionDataStore,
                onSignOut         = {
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
