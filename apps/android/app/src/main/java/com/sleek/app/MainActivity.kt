package com.sleek.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.remote.SocketManager
import com.sleek.app.ui.navigation.NavGraph
import com.sleek.app.ui.navigation.Screen
import com.sleek.app.ui.theme.SleekTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHAT_ID   = "extra_chat_id"
        const val EXTRA_CHAT_NAME = "extra_chat_name"
    }

    @Inject lateinit var tokenDataStore: TokenDataStore
    @Inject lateinit var socketManager:  SocketManager

    // Launcher for POST_NOTIFICATIONS permission request
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — we just silently accept the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val token = runBlocking { tokenDataStore.token.first() }
        val start = if (token != null) Screen.ChatList.route else Screen.Login.route

        token?.let { lifecycleScope.launch { socketManager.connect(it) } }
        splash.setKeepOnScreenCondition { false }

        // Read deep-link from notification tap (if any)
        val deepChatId   = intent.getStringExtra(EXTRA_CHAT_ID)
        val deepChatName = intent.getStringExtra(EXTRA_CHAT_NAME)

        setContent {
            SleekTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController, startDestination = start)

                // Navigate into the chat that the user tapped in the notification
                LaunchedEffect(deepChatId) {
                    if (deepChatId != null && token != null) {
                        navController.navigate(
                            Screen.Chat.createRoute(deepChatId, deepChatName ?: "Chat")
                        )
                    }
                }
            }
        }
    }
}
