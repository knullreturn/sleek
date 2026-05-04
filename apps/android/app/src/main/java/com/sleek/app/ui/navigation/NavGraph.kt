package com.sleek.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sleek.app.ui.auth.LoginScreen
import com.sleek.app.ui.auth.RegisterScreen
import com.sleek.app.ui.chat.ChatScreen
import com.sleek.app.ui.chatlist.ChatListScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Screen.Register.route) },
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onGoToLogin = { navController.popBackStack() },
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onOpenChat = { chatId, chatName ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatName))
                },
            )
        }

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
