package com.sleek.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sleek.app.ui.auth.LoginScreen
import com.sleek.app.ui.auth.OnboardingScreen
import com.sleek.app.ui.main.MainScreen

@Composable
fun NavGraph(
    navController:    NavHostController,
    startDestination: String,
    deepChatId:       String? = null,
    deepChatName:     String? = null,
    modifier:         Modifier = Modifier,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
        enterTransition  = {
            slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
            fadeIn(tween(260))
        },
        exitTransition   = {
            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
            fadeOut(tween(260))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
            fadeIn(tween(260))
        },
        popExitTransition = {
            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
            fadeOut(tween(260))
        },
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNeedsOnboard = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        // Main app — Chat, Profile, and ChatList all live inside MainScreen
        // ChatList is never destroyed when navigating between them
        composable(Screen.ChatList.route) {
            MainScreen(
                onLoggedOut  = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                deepChatId   = deepChatId,
                deepChatName = deepChatName,
            )
        }
    }
}
