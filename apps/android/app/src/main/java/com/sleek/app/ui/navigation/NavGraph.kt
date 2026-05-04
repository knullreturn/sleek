package com.sleek.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sleek.app.ui.auth.LoginScreen
import com.sleek.app.ui.auth.OnboardingScreen
import com.sleek.app.ui.chat.ChatScreen
import com.sleek.app.ui.chatlist.ChatListScreen

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
    ) {
        // ── Login — Google Sign-In ────────────────────────────────────────────
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

        // ── Onboarding — new user picks username ──────────────────────────────
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
                onOpenChat = { chatId, chatName ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatName))
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
