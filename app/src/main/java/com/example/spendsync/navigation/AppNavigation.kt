package com.example.spendsync.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.example.spendsync.ui.auth.AuthFlowScreen
import com.example.spendsync.ui.main.MainScreen
import com.example.spendsync.data.repository.hydrateSettingsFromBackend
import com.example.spendsync.data.repository.warmFinanceCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Top-level route constants ─────────────────────────────────────────────────

object Route {
    const val LOGIN    = "login"
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

    // The one way into the app after signing in or up. It is safe to call more than once: it only acts while
    // the auth screen is showing, so the event path and the session path below can never double-navigate.
    fun goHome() {
        if (navController.currentDestination?.route != Route.LOGIN) return
        scope.launch { hydrateSettingsFromBackend(financeRepository, sessionDataStore) }
        scope.launch { warmFinanceCache(financeRepository) }
        // Optional: email the user a copy of their data right after they sign in (Settings -> Data -> Email my data).
        scope.launch {
            if (sessionDataStore.exportOnLogin.first()) {
                financeRepository.emailExport(com.example.spendsync.data.remote.model.ExportEmailRequest(null, null, listOf("transactions", "holds", "plan"), "all", "csv"))
            }
        }
        // Clear the auth screen off the stack. Stack: [MAIN].
        navController.navigate(Route.MAIN) {
            popUpTo(Route.LOGIN) { inclusive = true }
        }
    }

    // Signed in = go home. Driven by the saved session itself, not only by a one-shot event, so a lost or
    // mis-delivered event during a screen transition can never leave the user stuck on the login screen.
    val sessionToken by sessionDataStore.sessionToken.collectAsState(initial = null)
    LaunchedEffect(sessionToken) {
        if (!sessionToken.isNullOrBlank()) goHome()
    }

    // Listen for 401 Unauthorized events from AuthInterceptor.
    // Clear session, cache, and navigate to Login with full back stack clear.
    LaunchedEffect(Unit) {
        AuthEvents.unauthorizedFlow.collect {
            sessionDataStore.clearSession()
            financeRepository.clearCache()
            // Already on the auth screen: nothing to redirect (re-navigating would reload it).
            if (navController.currentDestination?.route == Route.LOGIN) return@collect
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
                if (currentRoute != Route.LOGIN) {
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

        // ── Auth: log in, sign up and forgot password are pages of ONE screen (see AuthFlowScreen),
        // so switching between them never slides the whole design out and in again. ─────────────
        composable(route = Route.LOGIN) {
            AuthFlowScreen(
                viewModel        = authViewModel,
                onNavigateToHome = { goHome() },
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
