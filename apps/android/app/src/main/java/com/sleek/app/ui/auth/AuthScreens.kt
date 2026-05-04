package com.sleek.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleek.app.ui.auth.SleekTextField
import com.sleek.app.ui.theme.*

// ── Login Screen — Google Sign-In only ───────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess:  () -> Unit,
    onNeedsOnboard:  () -> Unit,
    viewModel:       AuthViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state) {
        when (state) {
            is AuthUiState.Success      -> onLoginSuccess()
            is AuthUiState.NeedsOnboard -> onNeedsOnboard()
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Logo ─────────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text  = "SLEEK",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize      = 42.sp,
                        brush         = Brush.linearGradient(listOf(Accent, AccentLight)),
                        letterSpacing = 8.sp,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Error ────────────────────────────────────────────────────────
            AnimatedVisibility(visible = state is AuthUiState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .border(1.dp, ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text  = (state as? AuthUiState.Error)?.message ?: "",
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ── Continue with Google button ───────────────────────────────────
            Button(
                onClick  = { viewModel.signInWithGoogle(context) },
                enabled  = state !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Color(0xFF1F1F1F),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color       = Color(0xFF1F1F1F),
                        modifier    = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.foundation.Image(
                            painter            = androidx.compose.ui.res.painterResource(com.sleek.app.R.drawable.ic_google),
                            contentDescription = "Google",
                            modifier           = androidx.compose.ui.Modifier.size(20.dp),
                        )
                        Text(
                            text  = "Continue with Google",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color    = Color(0xFF1F1F1F),
                                fontSize = 15.sp,
                            ),
                        )
                    }
                }
            }

            Text(
                text  = "By continuing you agree to our Terms of Service",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Onboarding Screen — pick a username ──────────────────────────────────────
@Composable
fun OnboardingScreen(
    onDone:    () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state    by viewModel.state.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text  = "Choose a username",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text      = "This is how people find you on SLEEK.\nYou can change it later.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            SleekTextField(
                value         = username,
                onValueChange = {
                    username = it.lowercase().replace(" ", "_")
                    viewModel.resetState()
                },
                label = "Username",
            )

            AnimatedVisibility(visible = state is AuthUiState.Error) {
                Text(
                    text  = (state as? AuthUiState.Error)?.message ?: "",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick  = { viewModel.submitUsername(username) },
                enabled  = username.length >= 2 && state !is AuthUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                shape    = RoundedCornerShape(12.dp),
            ) {
                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Get Started →", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}


// ── Shared outlined text field ─────────────────────────────────────────────
@Composable
fun SleekTextField(
    value:         String,
    onValueChange: (String) -> Unit,
    label:         String,
    modifier:      Modifier = Modifier,
    trailingIcon:  @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions =
        androidx.compose.foundation.text.KeyboardActions.Default,
) {
    OutlinedTextField(
        value             = value,
        onValueChange     = onValueChange,
        label             = { Text(label) },
        singleLine        = true,
        modifier          = modifier.fillMaxWidth(),
        trailingIcon      = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions   = keyboardOptions,
        keyboardActions   = keyboardActions,
        colors            = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Accent,
            unfocusedBorderColor = BorderSubtle,
            focusedLabelColor    = Accent,
            unfocusedLabelColor  = TextSecondary,
            cursorColor          = Accent,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
        ),
        shape = MaterialTheme.shapes.medium,
    )
}
