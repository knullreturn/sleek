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
import com.sleek.app.data.local.SettingsDataStore
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.remote.SocketManager
import com.sleek.app.ui.navigation.NavGraph
import com.sleek.app.ui.navigation.Screen
import com.sleek.app.ui.theme.SleekTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHAT_ID   = "extra_chat_id"
        const val EXTRA_CHAT_NAME = "extra_chat_name"
    }

    @Inject lateinit var tokenDataStore:    TokenDataStore
    @Inject lateinit var socketManager:     SocketManager
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* silently accept result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── Auth token — read async, hold splash until done ───────────────────
        // Fix: was runBlocking which blocked the main thread on every cold start.
        // Now we hold the splash screen while a coroutine reads the token off-thread,
        // then dismiss splash and render the first frame with the correct route.
        var startDestination: String? = null   // null = still loading
        var resolvedToken:    String? = null

        splash.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            resolvedToken    = tokenDataStore.token.first()  // fast DataStore read, not main thread
            startDestination = if (resolvedToken != null) Screen.ChatList.route else Screen.Login.route
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val deepChatId   = intent.getStringExtra(EXTRA_CHAT_ID)
        val deepChatName = intent.getStringExtra(EXTRA_CHAT_NAME)

        setContent {
            // Collect theme preference — default dark while loading
            val isDark by settingsDataStore.isDarkTheme.collectAsState(initial = true)
            // Wait for token read before rendering nav graph
            val start = startDestination

            SleekTheme(darkTheme = isDark) {
                if (start != null) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController    = navController,
                        startDestination = start,
                        deepChatId       = deepChatId,
                        deepChatName     = deepChatName,
                    )

                    // Fix: socket connects AFTER first frame is drawn, not before setContent.
                    // Staggering by 300ms means the first navigation animation gets the full
                    // GPU budget instead of competing with network + socket handshake.
                    LaunchedEffect(Unit) {
                        delay(300)
                        resolvedToken?.let { socketManager.connect(it) }
                    }
                }
            }
        }
    }
}
