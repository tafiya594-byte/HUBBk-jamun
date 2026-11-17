package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.repository.AuthAndProfileRepository
import com.example.ui.components.AbidAvatar
import com.example.util.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authRepo: AuthAndProfileRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authRepo.attemptSilentSignIn(context)
            .onSuccess { user -> if (user != null) onAuthSuccess() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_hero),
                        contentDescription = "ABID Messenger illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ABID MESSENGER",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time chats, 24-hour statuses, voice notes, documents & live WebRTC calls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FeatureRow(Icons.Default.VerifiedUser, "Multi-user rooms & unique @usernames")
                        FeatureRow(Icons.Default.VideoCall, "Real WebRTC HD audio & video calling")
                        FeatureRow(Icons.Default.Lock, "Protected by Firebase zero-trust security rules")
                    }
                }

                if (errorMsg != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            errorMsg = "Unable to launch Google Sign-In from this context."
                            return@Button
                        }
                        errorMsg = null
                        isLoading = true
                        scope.launch {
                            authRepo.signInWithGoogleInteractive(activity)
                                .onSuccess {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                                .onFailure { err ->
                                    isLoading = false
                                    errorMsg = if (err is GetCredentialCancellationException) {
                                        "Sign-in was cancelled. Tap Continue with Google to try again."
                                    } else {
                                        err.localizedMessage ?: "Sign-in failed. Please try again."
                                    }
                                }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Text(
                    text = "You'll choose your unique @username and profile details right after signing in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ProfileSetupScreen(
    isBusy: Boolean,
    usernameAvailable: Boolean?,
    onCheckUsername: (String) -> Unit,
    onCreateProfile: (username: String, displayName: String, about: String, avatarData: String?) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = remember { Firebase.auth.currentUser }
    val suggestedName = remember { currentUser?.displayName?.take(50).orEmpty() }
    val suggestedHandle = remember {
        (currentUser?.email?.substringBefore("@") ?: "user")
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .take(18)
            .let { if (it.length < 3) "${it}_abid" else it }
    }

    var displayName by remember { mutableStateOf(suggestedName) }
    var username by remember { mutableStateOf(suggestedHandle) }
    var about by remember { mutableStateOf("Hey there! I am using ABID Messenger.") }
    var avatarData by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            MediaUtils.compressImageUriToBase64(context, uri, maxDimension = 512, maxBytes = 140_000)
                .onSuccess { avatarData = it }
        }
    }

    LaunchedEffect(username) {
        onCheckUsername(username)
    }

    val validUsernameFormat = username.trim().lowercase().matches(Regex("^[a-z0-9_]{3,24}$"))

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Create Your Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose a unique @username so friends can find and message you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AbidAvatar(
                    name = displayName.ifBlank { "A" },
                    avatarData = avatarData,
                    size = 96.dp
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Choose profile photo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 50) displayName = it },
                label = { Text("Display Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("setup_display_name_input")
            )

            OutlinedTextField(
                value = username,
                onValueChange = { input ->
                    val cleaned = input.lowercase().filter { it.isLetterOrDigit() || it == '_' }
                    if (cleaned.length <= 24) username = cleaned
                },
                label = { Text("Unique Username") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                trailingIcon = {
                    when {
                        !validUsernameFormat -> Icon(Icons.Default.ErrorOutline, contentDescription = "Invalid", tint = MaterialTheme.colorScheme.error)
                        usernameAvailable == true -> Icon(Icons.Default.CheckCircle, contentDescription = "Available", tint = MaterialTheme.colorScheme.primary)
                        usernameAvailable == false -> Icon(Icons.Default.ErrorOutline, contentDescription = "Taken", tint = MaterialTheme.colorScheme.error)
                    }
                },
                supportingText = {
                    val hint = when {
                        !validUsernameFormat -> "Use 3–24 lowercase letters, numbers, or underscores"
                        usernameAvailable == true -> "@$username is available"
                        usernameAvailable == false -> "@$username is already taken"
                        else -> "Checking availability..."
                    }
                    Text(hint)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("setup_username_input")
            )

            OutlinedTextField(
                value = about,
                onValueChange = { if (it.length <= 160) about = it },
                label = { Text("About") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("setup_about_input")
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onCreateProfile(username, displayName, about, avatarData) },
                enabled = !isBusy && displayName.trim().isNotEmpty() && validUsernameFormat && usernameAvailable != false,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("complete_profile_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Start Messaging")
                }
            }

            androidx.compose.material3.TextButton(onClick = onSignOut) {
                Text("Use a different Google account")
            }
        }
    }
}
