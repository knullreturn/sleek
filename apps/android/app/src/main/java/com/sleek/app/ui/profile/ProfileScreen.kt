package com.sleek.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onBack:    () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val me       by viewModel.me.collectAsStateWithLifecycle()
    val loggedOut by viewModel.loggedOut.collectAsStateWithLifecycle()

    LaunchedEffect(loggedOut) {
        if (loggedOut) onBack() // MainActivity will detect no token and show Login
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title  = { Text("Profile", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Avatar
            if (me?.avatarUrl != null) {
                AsyncImage(
                    model             = me!!.avatarUrl,
                    contentDescription = "Avatar",
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier.size(90.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(AccentDim),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = (me?.username ?: "?").take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color    = Accent,
                            fontSize = 36.sp,
                        ),
                    )
                }
            }

            // Name + tag
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = me?.username ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text  = "#${me?.tag ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.weight(1f))

            // Logout
            FilledTonalButton(
                onClick  = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.filledTonalButtonColors(
                    containerColor = ErrorRed.copy(alpha = 0.12f),
                    contentColor   = ErrorRed,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
