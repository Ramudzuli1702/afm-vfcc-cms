package com.afmvfcc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.afmvfcc.app.ui.*
import com.afmvfcc.app.ui.screens.*
import com.afmvfcc.app.ui.theme.AfmTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AfmTheme {
                AppNavigation(viewModel)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val isLoggedIn    by viewModel.isLoggedIn.collectAsState()

    val startDestination = remember(isLoggedIn) {
        if (isLoggedIn) Screen.Sessions.route else Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Sessions.route) {
            SessionsScreen(
                viewModel = viewModel,
                onSessionSelected = { session ->
                    navController.navigate(Screen.Attendance.route(session.id, session.name))
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.Attendance.route,
            arguments = listOf(
                navArgument("sessionId")   { type = NavType.IntType },
                navArgument("sessionName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId   = backStackEntry.arguments?.getInt("sessionId")   ?: 0
            val sessionName = backStackEntry.arguments?.getString("sessionName") ?: ""
            AttendanceScreen(
                sessionId   = sessionId,
                sessionName = sessionName,
                viewModel   = viewModel,
                onAddGuest  = { navController.navigate(Screen.AddGuest.route(sessionId)) },
                onBack      = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddGuest.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getInt("sessionId") ?: 0
            AddGuestScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }
    }

    // React to login state changes
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Screen.Sessions.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
}
