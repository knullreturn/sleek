package com.sleek.app.ui.navigation

sealed class Screen(val route: String) {
    object Login      : Screen("login")
    object Onboarding : Screen("onboarding")
    object ChatList   : Screen("chat_list")
    object Chat       : Screen("chat/{chatId}/{chatName}") {
        fun createRoute(chatId: String, chatName: String) = "chat/$chatId/$chatName"
    }
    object Profile    : Screen("profile")
}
