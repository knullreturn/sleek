package com.sleek.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sleek.app.ui.auth.LoginScreen
import com.sleek.app.ui.auth.OnboardingScreen
import com.sleek.app.ui.chat.ChatScreen
import com.sleek.app.ui.chatlist.ChatListScreen
import com.sleek.app.ui.profile.ProfileScreen

// Smooth spring-like slide — feels natural, not mechanical
private val slideSpec = tween<IntOffset>(340, easing = FastOutSlowInEasing)
private val fadeSpec  = tween<Float>(260, easing = FastOutSlowInEasing)

@Composable
fun NavGraph(
    navController:    NavHostController,
    startDestination: String,
    modifier:         Modifier = Modifier,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
        // ── Default transitions: horizontal slide like Telegram/WhatsApp ───────
        enterTransition = {
            slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)
        },
        exitTransition = {
            slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec)
        },
        popEnterTransition = {
            slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec)
        },
        popExitTransition = {
            slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec)
        },
    ) {
        // ── Login ─────────────────────────────────────────────────────────────
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

        // ── Onboarding ────────────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        // ── Chat list ─────────────────────────────────────────────────────────
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onOpenChat    = { chatId, chatName ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatName))
                },
                onOpenProfile = { navController.navigate(Screen.Profile.route) },
            )
        }

        // ── Profile ───────────────────────────────────────────────────────────
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack      = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }   // clear entire back stack
                    }
                },
            )
        }

        // ── Chat ──────────────────────────────────────────────────────────────
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId")   { type = NavType.StringType },
                navArgument("chatName") { type = NavType.StringType },
            ),
        ) { backStack ->
            val chatId   = backStack.arguments?.getString("chatId")   ?: return@composable
            val chatName = backStack.arguments?.getString("chatName") ?: "Chat"
            ChatScreen(
                chatId   = chatId,
                chatName = chatName,
                onBack   = { navController.popBackStack() },
            )
        }
    }
}
