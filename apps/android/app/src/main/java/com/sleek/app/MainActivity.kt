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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.sleek.app.data.local.SettingsDataStore
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.remote.SocketManager
import com.sleek.app.notification.NotificationHelper
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

        // mutableStateOf = Compose observes changes. Plain var (old code) was read
        // once at first composition (null) and never triggered recomposition when
        // the coroutine set the route — causing a permanent black screen.
        var startDestination by mutableStateOf<String?>(null)
        var resolvedToken    by mutableStateOf<String?>(null)

        splash.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            resolvedToken    = tokenDataStore.token.first()
            startDestination = if (resolvedToken != null) Screen.ChatList.route else Screen.Login.route
        }

        // ── Fix: reconnect on resume + clear active chat when backgrounded ────
        // DefaultLifecycleObserver fires for every start/stop of the activity.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // App came to foreground (or first open) — ensure socket is alive.
                // If the socket died while we were in background (network change,
                // server restart, phone sleep), this forces a fresh reconnect.
                lifecycleScope.launch {
                    val token = tokenDataStore.token.first() ?: return@launch
                    socketManager.reconnectIfNeeded(token)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                // App went to background. Clear the active chat so FCM notifications
                // are NOT suppressed while the app is invisible.
                // Previously this was only cleared in ChatViewModel.onCleared() which
                // only fires when the ViewModel is truly destroyed, not on backgrounding.
                NotificationHelper.activeChatId = null
            }
        })

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
            val isDark by settingsDataStore.isDarkTheme.collectAsState(initial = true)
            val start  = startDestination

            SleekTheme(darkTheme = isDark) {
                if (start != null) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController    = navController,
                        startDestination = start,
                        deepChatId       = deepChatId,
                        deepChatName     = deepChatName,
                    )

                    // Fix: socket connects AFTER first frame, not before setContent.
                    // Frees GPU budget for the opening navigation animation.
                    LaunchedEffect(Unit) {
                        delay(300)
                        resolvedToken?.let { socketManager.connect(it) }
                    }
                }
            }
        }
    }
}
