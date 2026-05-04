package com.sleek.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleek.app.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess:  () -> Unit,
    onGoToRegister:  () -> Unit,
    viewModel:       AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo / wordmark
            Text(
                text  = "SLEEK",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize   = 36.sp,
                    brush      = Brush.linearGradient(listOf(Accent, AccentLight)),
                    letterSpacing = 6.sp,
                ),
            )
            Text(
                text  = "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Email
            SleekTextField(
                value       = email,
                onValueChange = { email = it },
                label       = "Email",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
            )

            // Password
            SleekTextField(
                value         = password,
                onValueChange = { password = it },
                label         = "Password",
                visualTransformation = if (showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            imageVector = if (showPass) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.login(email, password)
                    }
                ),
            )

            // Error
            AnimatedVisibility(visible = state is AuthUiState.Error) {
                Text(
                    text  = (state as? AuthUiState.Error)?.message ?: "",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Sign in button
            Button(
                onClick  = { viewModel.login(email, password) },
                enabled  = email.isNotBlank() && password.isNotBlank() && state !is AuthUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                shape    = MaterialTheme.shapes.medium,
            ) {
                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color = TextPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sign In", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Register link
            TextButton(onClick = onGoToRegister) {
                Text(
                    text  = "Don't have an account? Create one",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onGoToLogin:       () -> Unit,
    viewModel:         AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onRegisterSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "SLEEK",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize      = 36.sp,
                    brush         = Brush.linearGradient(listOf(Accent, AccentLight)),
                    letterSpacing = 6.sp,
                ),
            )
            Text("Create your account", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))

            SleekTextField(
                value         = email,
                onValueChange = { email = it },
                label         = "Email",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
            )

            SleekTextField(
                value         = password,
                onValueChange = { password = it },
                label         = "Password",
                visualTransformation = if (showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            imageVector = if (showPass) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.register(email, password)
                    }
                ),
            )

            AnimatedVisibility(visible = state is AuthUiState.Error) {
                Text(
                    text  = (state as? AuthUiState.Error)?.message ?: "",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = { viewModel.register(email, password) },
                enabled  = email.isNotBlank() && password.isNotBlank() && state !is AuthUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                shape    = MaterialTheme.shapes.medium,
            ) {
                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Account", style = MaterialTheme.typography.titleMedium)
                }
            }

            TextButton(onClick = onGoToLogin) {
                Text("Already have an account? Sign in", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ── Shared text field ─────────────────────────────────────────────────────────
@Composable
fun SleekTextField(
    value:                String,
    onValueChange:        (String) -> Unit,
    label:                String,
    modifier:             Modifier = Modifier,
    trailingIcon:         @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions:      KeyboardOptions = KeyboardOptions.Default,
    keyboardActions:      KeyboardActions = KeyboardActions.Default,
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
