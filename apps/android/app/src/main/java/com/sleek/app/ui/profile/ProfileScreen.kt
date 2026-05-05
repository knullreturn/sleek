package com.sleek.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sleek.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack:      () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel:   ProfileViewModel = hiltViewModel(),
) {
    val me           by viewModel.me.collectAsStateWithLifecycle()
    val email        by viewModel.email.collectAsStateWithLifecycle()
    val loggedOut    by viewModel.loggedOut.collectAsStateWithLifecycle()
    val isDark       by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val notifsOn     by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val sleepMode    by viewModel.sleepModeEnabled.collectAsStateWithLifecycle()

    val bg      = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val onBg    = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(loggedOut) { if (loggedOut) onLoggedOut() }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title  = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface,
                    titleContentColor = onBg,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Profile Card ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Avatar — colored circle while loading, crossfade to photo when ready
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(AccentDim),
                    contentAlignment = Alignment.Center,
                ) {
                    // AsyncImage handles null model gracefully — shows nothing (AccentDim bg visible)
                    // When avatarUrl loads, it crossfades in over the background
                    AsyncImage(
                        model            = me?.avatarUrl,
                        contentDescription = "Avatar",
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize(),
                    )
                }

                // Name + tag — only render when me is loaded (avoids "#" → "#tag" flash)
                if (me != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text  = me?.username ?: "—",
                            style = MaterialTheme.typography.headlineSmall.copy(color = onBg),
                        )
                        Text(
                            text  = "#${me?.tag ?: ""}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        if (email != null) {
                            Text(
                                text  = email!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Appearance ───────────────────────────────────────────────────
            SectionLabel("Appearance")
            SettingsCard {
                SettingsToggleRow(
                    icon    = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title   = "Theme",
                    subtitle = if (isDark) "Dark" else "Light",
                    checked = isDark,
                    onCheckedChange = { viewModel.setDarkTheme(it) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Notifications ────────────────────────────────────────────────
            SectionLabel("Notifications")
            SettingsCard {
                SettingsToggleRow(
                    icon    = if (notifsOn) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    title   = "Push Notifications",
                    subtitle = if (notifsOn) "Enabled" else "Muted",
                    checked = notifsOn,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Sleep Mode ───────────────────────────────────────────────────
            SectionLabel("Focus")
            SettingsCard {
                SettingsToggleRow(
                    icon    = Icons.Default.Bedtime,
                    title   = "Sleep Mode",
                    subtitle = if (sleepMode) "Active — shown in chat header" else "Off",
                    checked = sleepMode,
                    onCheckedChange = { viewModel.setSleepMode(it) },
                    accentWhenOn = Accent.copy(alpha = 0.12f),
                )
            }

            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(32.dp))

            // ── Logout ───────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                FilledTonalButton(
                    onClick  = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ErrorRed.copy(alpha = 0.12f),
                        contentColor   = ErrorRed,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", style = MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            color       = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        ),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
        content  = content,
    )
}

@Composable
private fun SettingsToggleRow(
    icon:           ImageVector,
    title:          String,
    subtitle:       String,
    checked:        Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentWhenOn:   androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) accentWhenOn else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint   = if (checked) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor       = Accent,
                checkedTrackColor       = Accent.copy(alpha = 0.3f),
                uncheckedThumbColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor     = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
