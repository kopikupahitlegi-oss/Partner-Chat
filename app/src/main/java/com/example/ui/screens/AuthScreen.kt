package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.data.IdAuthMode
import com.example.data.PathnerIdHelper
import com.example.data.PendingIdAuthRequest
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import kotlinx.coroutines.delay

private enum class AuthTab {
    LOGIN, REGISTER
}

@Composable
fun AuthScreen(
    savedPathnerId: String? = null,
    externalErrorMessage: String? = null,
    initialOnRegisterTab: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onClearExternalError: () -> Unit = {},
    onPrepareIdAuth: (PendingIdAuthRequest?) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var activeTab by remember {
        mutableStateOf(if (initialOnRegisterTab) AuthTab.REGISTER else AuthTab.LOGIN)
    }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSession by remember { mutableStateOf(externalErrorMessage == null) }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }

    // Single ID-only inputs for Masuk & Daftar
    var loginIdInput by remember(savedPathnerId) { mutableStateOf(savedPathnerId.orEmpty()) }
    var loginIdError by remember { mutableStateOf<String?>(null) }
    var registerIdInput by remember { mutableStateOf("") }
    var registerIdError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialOnRegisterTab) {
        if (initialOnRegisterTab) {
            activeTab = AuthTab.REGISTER
        }
    }

    LaunchedEffect(Unit) {
        if (externalErrorMessage != null) {
            isCheckingSession = false
            return@LaunchedEffect
        }
        onPrepareIdAuth(null)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSession = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSession = false
            },
            scope = scope
        )
    }

    val combinedError = localErrorMessage ?: externalErrorMessage

    val launchGoogleFlow: (PendingIdAuthRequest?) -> Unit = { request ->
        localErrorMessage = null
        onClearExternalError()
        onPrepareIdAuth(request)
        isLoading = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                onPrepareIdAuth(null)
                isLoading = false
                localErrorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                onPrepareIdAuth(null)
                isLoading = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EmeraldDeep,
                        EmeraldPrimary,
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f,
                    endY = 980f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand Header with Quick Dark Mode Toggle
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_partner_bank_avatar_ref),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    IconButton(
                        onClick = { onToggleDarkMode(!isDarkTheme) },
                        modifier = Modifier.testTag("auth_dark_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = stringResource(
                                if (isDarkTheme) R.string.dark_mode_toggle_to_light_desc
                                else R.string.dark_mode_toggle_to_dark_desc
                            ),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Artwork Banner
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_hero_1795062092113),
                    contentDescription = stringResource(R.string.auth_headline),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ID-Only Masuk / Daftar Card
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Tab Bar (Masuk / Daftar)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            AuthSegmentButton(
                                label = stringResource(R.string.auth_tab_login),
                                selected = activeTab == AuthTab.LOGIN,
                                testTag = "auth_tab_login",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    activeTab = AuthTab.LOGIN
                                    localErrorMessage = null
                                    onClearExternalError()
                                    registerIdError = null
                                }
                            )
                            AuthSegmentButton(
                                label = stringResource(R.string.auth_tab_register),
                                selected = activeTab == AuthTab.REGISTER,
                                testTag = "auth_tab_register",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    activeTab = AuthTab.REGISTER
                                    localErrorMessage = null
                                    onClearExternalError()
                                    loginIdError = null
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "auth_mode_switch"
                    ) { tab ->
                        if (tab == AuthTab.LOGIN) {
                            LoginByIdTabBody(
                                pathnerId = loginIdInput,
                                savedPathnerId = savedPathnerId,
                                onPathnerIdChange = { raw ->
                                    val sanitized = raw.replace(" ", "").take(33)
                                    loginIdInput = sanitized
                                    loginIdError = null
                                    localErrorMessage = null
                                    onClearExternalError()
                                },
                                onUseSavedId = { saved ->
                                    loginIdInput = saved
                                    loginIdError = null
                                    localErrorMessage = null
                                    onClearExternalError()
                                },
                                idError = loginIdError,
                                isLoading = isLoading || isCheckingSession,
                                errorMessage = combinedError,
                                onLoginWithId = {
                                    val validation = PathnerIdHelper.validationError(loginIdInput)
                                    if (validation != null) {
                                        loginIdError = validation
                                        return@LoginByIdTabBody
                                    }
                                    val normalized = PathnerIdHelper.normalize(loginIdInput)
                                    launchGoogleFlow(
                                        PendingIdAuthRequest(
                                            mode = IdAuthMode.LOGIN,
                                            pathnerId = normalized
                                        )
                                    )
                                },
                                onQuickGoogleLogin = {
                                    loginIdError = null
                                    launchGoogleFlow(null)
                                },
                                onSwitchToRegister = {
                                    activeTab = AuthTab.REGISTER
                                    localErrorMessage = null
                                    onClearExternalError()
                                    loginIdError = null
                                }
                            )
                        } else {
                            RegisterByIdTabBody(
                                pathnerId = registerIdInput,
                                onPathnerIdChange = { raw ->
                                    val sanitized = raw.replace(" ", "").take(33)
                                    registerIdInput = sanitized
                                    registerIdError = null
                                    localErrorMessage = null
                                    onClearExternalError()
                                },
                                onGenerateRandomId = {
                                    registerIdInput = PathnerIdHelper.generateRandomId()
                                    registerIdError = null
                                    localErrorMessage = null
                                    onClearExternalError()
                                },
                                idError = registerIdError,
                                isLoading = isLoading || isCheckingSession,
                                errorMessage = combinedError,
                                onRegisterWithId = {
                                    val validation = PathnerIdHelper.validationError(registerIdInput)
                                    if (validation != null) {
                                        registerIdError = validation
                                        return@RegisterByIdTabBody
                                    }
                                    val normalized = PathnerIdHelper.normalize(registerIdInput)
                                    launchGoogleFlow(
                                        PendingIdAuthRequest(
                                            mode = IdAuthMode.REGISTER,
                                            pathnerId = normalized
                                        )
                                    )
                                },
                                onSwitchToLogin = {
                                    activeTab = AuthTab.LOGIN
                                    localErrorMessage = null
                                    onClearExternalError()
                                    registerIdError = null
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.auth_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuthSegmentButton(
    label: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldPrimary else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoginByIdTabBody(
    pathnerId: String,
    savedPathnerId: String?,
    onPathnerIdChange: (String) -> Unit,
    onUseSavedId: (String) -> Unit,
    idError: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onLoginWithId: () -> Unit,
    onQuickGoogleLogin: () -> Unit,
    onSwitchToRegister: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.auth_login_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.auth_login_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pathnerId,
            onValueChange = onPathnerIdChange,
            label = { Text(stringResource(R.string.auth_id_label)) },
            placeholder = { Text(stringResource(R.string.auth_id_hint)) },
            prefix = {
                Text(
                    text = "@",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.AlternateEmail,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
            },
            isError = idError != null,
            supportingText = {
                Text(idError ?: stringResource(R.string.auth_id_helper))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!isLoading) onLoginWithId() }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_id_input")
        )

        if (!savedPathnerId.isNullOrBlank() && PathnerIdHelper.normalize(pathnerId) != savedPathnerId) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                onClick = { onUseSavedId(savedPathnerId) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.testTag("use_saved_id_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Badge,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.auth_saved_id_chip, savedPathnerId),
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            AuthErrorBanner(errorMessage)
        }

        GoogleAuthActionButton(
            text = stringResource(R.string.auth_sign_in_id_google),
            isLoading = isLoading,
            testTag = "google_sign_in_button",
            onClick = onLoginWithId
        )

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(
            onClick = onQuickGoogleLogin,
            enabled = !isLoading,
            modifier = Modifier.testTag("quick_google_sign_in_button")
        ) {
            Text(
                text = stringResource(R.string.auth_quick_google_login),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            onClick = onSwitchToRegister,
            modifier = Modifier.testTag("switch_to_register_button")
        ) {
            Text(
                text = stringResource(R.string.auth_switch_to_register),
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldPrimary
            )
        }
    }
}

@Composable
private fun RegisterByIdTabBody(
    pathnerId: String,
    onPathnerIdChange: (String) -> Unit,
    onGenerateRandomId: () -> Unit,
    idError: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onRegisterWithId: () -> Unit,
    onSwitchToLogin: () -> Unit
) {
    val context = LocalContext.current
    val normalizedPreview = remember(pathnerId) { PathnerIdHelper.normalize(pathnerId) }
    val isPreviewValid = remember(normalizedPreview) { PathnerIdHelper.isValid(normalizedPreview) }
    var justCopied by remember(normalizedPreview) { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1800L)
            justCopied = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.auth_register_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.auth_register_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pathnerId,
            onValueChange = onPathnerIdChange,
            label = { Text(stringResource(R.string.auth_register_id_label)) },
            placeholder = { Text(stringResource(R.string.auth_id_hint)) },
            prefix = {
                Text(
                    text = "@",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Badge,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = onGenerateRandomId,
                    modifier = Modifier.testTag("generate_random_id_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.auth_generate_id_desc),
                        tint = EmeraldPrimary
                    )
                }
            },
            isError = idError != null,
            supportingText = {
                Text(idError ?: stringResource(R.string.auth_id_helper))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!isLoading) onRegisterWithId() }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_id_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Prominent one-tap Create/Generate ID button
        OutlinedButton(
            onClick = onGenerateRandomId,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("generate_random_id_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auth_generate_id_button),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Live User ID Preview Card when ID is valid
        AnimatedVisibility(visible = isPreviewValid) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .border(
                        width = 1.dp,
                        color = EmeraldPrimary.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("created_id_preview_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auth_created_id_preview_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "@$normalizedPreview",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Partner Bank ID", normalizedPreview))
                            justCopied = true
                        },
                        modifier = Modifier.testTag("copy_preview_id_button")
                    ) {
                        Icon(
                            imageVector = if (justCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.profile_copy_id),
                            tint = EmeraldPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            AuthErrorBanner(errorMessage)
        }

        GoogleAuthActionButton(
            text = stringResource(R.string.auth_register_id_google),
            isLoading = isLoading,
            testTag = "google_register_button",
            onClick = onRegisterWithId
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onSwitchToLogin,
            modifier = Modifier.testTag("switch_to_login_button")
        ) {
            Text(
                text = stringResource(R.string.auth_switch_to_login),
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldPrimary
            )
        }
    }
}

@Composable
private fun AuthErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("auth_error_banner")
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GoogleAuthActionButton(
    text: String,
    isLoading: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmeraldPrimary,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(testTag)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "G",
                    color = EmeraldDeep,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
