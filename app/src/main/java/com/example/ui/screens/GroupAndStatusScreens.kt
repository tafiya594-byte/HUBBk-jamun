package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StatusUpdate
import com.example.data.model.UserPublic
import com.example.ui.components.AbidAvatar
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.MediaUtils
import com.example.util.TimeFormatters
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    currentUid: String,
    allUsers: List<UserPublic>,
    usersById: Map<String, UserPublic>,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onLeftGroup: () -> Unit
) {
    val context = LocalContext.current
    val chat by chatViewModel.chat.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }

    val activeChat = chat
    val isAdmin = activeChat != null && currentUid in activeChat.adminUids

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Group Info") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit group")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (activeChat == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading group...")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AbidAvatar(activeChat.title, activeChat.groupAvatarData, size = 84.dp, isGroup = true)
                    Text(activeChat.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (activeChat.description.isNotBlank()) {
                        Text(
                            activeChat.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text("${activeChat.memberUids.size} participants", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (isAdmin) {
                item {
                    Button(
                        onClick = { showAddMemberSheet = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_group_member_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Member")
                    }
                }
            }

            item {
                Text("Members", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            items(activeChat.memberUids, key = { it }) { uid ->
                val user = usersById[uid]
                val memberIsAdmin = uid in activeChat.adminUids
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AbidAvatar(user?.displayName ?: "User", user?.avatarData, size = 44.dp, isOnline = user?.online == true)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uid == currentUid) "You (${user?.displayName.orEmpty()})" else user?.displayName ?: uid.take(10),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (user != null) {
                            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (memberIsAdmin) {
                        AssistChip(onClick = {}, label = { Text("Admin") })
                    }
                    if (isAdmin && uid != currentUid) {
                        IconButton(onClick = { chatViewModel.toggleAdmin(uid, !memberIsAdmin) }) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Toggle admin")
                        }
                        IconButton(onClick = { chatViewModel.removeMember(uid) }) {
                            Icon(Icons.Default.PersonRemove, contentDescription = "Remove member", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        chatViewModel.removeMember(currentUid)
                        onLeftGroup()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("leave_group_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Leave Group")
                }
            }
        }

        if (showEditDialog) {
            var title by remember { mutableStateOf(activeChat.title) }
            var desc by remember { mutableStateOf(activeChat.description) }
            var avatar by remember { mutableStateOf(activeChat.groupAvatarData) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    MediaUtils.compressImageUriToBase64(context, uri, 512, 140_000)
                        .onSuccess { avatar = it }
                }
            }

            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Group") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text("Change Group Icon")
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = { if (it.length <= 80) title = it },
                            label = { Text("Group Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { if (it.length <= 250) desc = it },
                            label = { Text("Description") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            chatViewModel.updateGroupInfo(title, desc, avatar) {
                                showEditDialog = false
                            }
                        },
                        enabled = title.trim().isNotEmpty()
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
            )
        }

        if (showAddMemberSheet) {
            val nonMembers = allUsers.filter { it.uid !in activeChat.memberUids }
            ModalBottomSheet(onDismissRequest = { showAddMemberSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Add Member", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (nonMembers.isEmpty()) {
                        Text("All registered users are already in this group.", modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.height(280.dp)) {
                            items(nonMembers, key = { it.uid }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            chatViewModel.addMember(user.uid)
                                            showAddMemberSheet = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AbidAvatar(user.displayName, user.avatarData, size = 42.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusComposerDialog(
    initialTab: Int,
    defaultPrivacy: String,
    onDismiss: () -> Unit,
    onPost: (type: String, text: String, bgHex: String, mediaData: String?, privacy: String) -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(initialTab) }
    var text by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(defaultPrivacy) }
    val bgPalette = listOf("#008955", "#04875B", "#0D47A1", "#6A1B9A", "#B71C1C", "#263238")
    var selectedBg by remember { mutableStateOf(bgPalette.first()) }
    var mediaType by remember { mutableStateOf("image") }
    var mediaData by remember { mutableStateOf<String?>(null) }
    var mediaLabel by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            MediaUtils.compressImageUriToBase64(context, uri)
                .onSuccess {
                    mediaType = "image"
                    mediaData = it
                    mediaLabel = "Photo selected"
                }
                .onFailure { Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show() }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            MediaUtils.readUriToBase64(context, uri)
                .onSuccess {
                    mediaType = "video"
                    mediaData = it.base64
                    mediaLabel = it.fileName
                }
                .onFailure { Toast.makeText(context, it.message ?: "Video exceeds 520 KB inline limit", Toast.LENGTH_LONG).show() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Create 24h Status", style = MaterialTheme.typography.titleLarge)
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Text") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Photo / Video") })
                }

                if (tab == 0) {
                    val colorInt = android.graphics.Color.parseColor(selectedBg)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(colorInt))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text.ifBlank { "Type your status update..." },
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bgPalette.forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(
                                        if (selectedBg == hex) Modifier.border(2.5.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedBg = hex }
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Photo")
                        }
                        OutlinedButton(
                            onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Video")
                        }
                    }
                    if (mediaLabel.isNotBlank()) {
                        Text(mediaLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    label = { Text(if (tab == 0) "Status text" else "Caption (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("status_text_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible to:", style = MaterialTheme.typography.labelMedium)
                    FilterChip(
                        selected = privacy == "everyone",
                        onClick = { privacy = "everyone" },
                        label = { Text("Everyone") }
                    )
                    FilterChip(
                        selected = privacy == "contacts",
                        onClick = { privacy = "contacts" },
                        label = { Text("Contacts") }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (tab == 0) {
                                onPost("text", text, selectedBg, null, privacy)
                            } else {
                                onPost(mediaType, text, selectedBg, mediaData, privacy)
                            }
                        },
                        enabled = (tab == 0 && text.trim().isNotEmpty()) || (tab == 1 && !mediaData.isNullOrBlank()),
                        modifier = Modifier.testTag("post_status_button")
                    ) {
                        Text("Post")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewerScreen(
    currentUid: String,
    statuses: List<StatusUpdate>,
    usersById: Map<String, UserPublic>,
    onMarkViewed: (String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var showViewersSheet by remember { mutableStateOf(false) }

    if (statuses.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val safeIndex = index.coerceIn(0, statuses.lastIndex)
    val current = statuses[safeIndex]
    val isMine = current.userId == currentUid

    LaunchedEffect(current.id) {
        if (!isMine && currentUid !in current.viewerUids) {
            onMarkViewed(current.id)
        }
    }

    LaunchedEffect(safeIndex, showViewersSheet) {
        if (!showViewersSheet) {
            delay(5000L)
            if (safeIndex < statuses.lastIndex) index += 1 else onBack()
        }
    }

    val bgColor = remember(current.backgroundHex) {
        try {
            Color(android.graphics.Color.parseColor(current.backgroundHex))
        } catch (_: Exception) {
            Color(0xFF008955)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (current.type == "text") bgColor else Color.Black)
    ) {
        // Content
        when (current.type) {
            "text" -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = current.text,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            "image" -> {
                val bmp = remember(current.mediaData) { MediaUtils.decodeBase64Bitmap(current.mediaData) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Status photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                if (current.text.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 84.dp)
                    ) {
                        Text(
                            text = current.text,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            "video" -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            val b64 = current.mediaData
                            if (!b64.isNullOrBlank()) {
                                MediaUtils.openBase64Attachment(context, b64, "status_video.mp4", "video/*")
                            }
                        },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play video status", tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                    Text("Tap to play video status", color = Color.White)
                    if (current.text.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(current.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Tap navigation areas
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().clickable {
                    if (safeIndex > 0) index -= 1
                }
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().clickable {
                    if (safeIndex < statuses.lastIndex) index += 1 else onBack()
                }
            )
        }

        // Top Bar overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                statuses.forEachIndexed { idx, _ ->
                    LinearProgressIndicator(
                        progress = { if (idx <= safeIndex) 1f else 0.25f },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AbidAvatar(current.authorName, current.authorAvatar, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(current.authorName, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(
                        TimeFormatters.formatChatListTime(current.createdAt),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (isMine) {
                    IconButton(onClick = { onDeleteStatus(current.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete status", tint = Color.White)
                    }
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }

        if (isMine) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .clickable { showViewersSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Viewed by ${current.viewerUids.size}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showViewersSheet) {
        ModalBottomSheet(onDismissRequest = { showViewersSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Status Viewers (${current.viewerUids.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                if (current.viewerUids.isEmpty()) {
                    Text("No views yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(current.viewerUids) { vUid ->
                            val viewer = usersById[vUid]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AbidAvatar(viewer?.displayName ?: "User", viewer?.avatarData, size = 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(viewer?.displayName ?: vUid.take(12), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
