package com.sleek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
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

    @Inject lateinit var tokenDataStore: TokenDataStore
    @Inject lateinit var socketManager:  SocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Determine start destination synchronously (fast DataStore read)
        val token = runBlocking { tokenDataStore.token.first() }
        val start = if (token != null) Screen.ChatList.route else Screen.Login.route

        // Reconnect socket if token exists
        token?.let { lifecycleScope.launch { socketManager.connect(it) } }

        // Keep splash visible until determined
        splash.setKeepOnScreenCondition { false }

        setContent {
            SleekTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController, startDestination = start)
            }
        }
    }
}
