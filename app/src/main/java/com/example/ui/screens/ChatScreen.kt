package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.UiState
import com.example.data.model.UserPrivate
import com.example.data.model.UserPublic
import com.example.ui.components.AbidAvatar
import com.example.ui.components.ReportUserDialog
import com.example.ui.theme.LocalAbidColors
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.MediaUtils
import com.example.util.TimeFormatters
import com.example.util.VoicePlayerManager
import com.example.util.VoiceRecorderManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    currentUid: String,
    myProfile: UserPublic,
    myPrivate: UserPrivate?,
    allChats: List<Chat>,
    usersById: Map<String, UserPublic>,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenGroupInfo: (String) -> Unit,
    onStartCall: (UserPublic, String) -> Unit,
    onForwardMessage: (Chat, Message) -> Unit,
    onToggleMuted: (String, Boolean) -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onSubmitReport: (String, String?, String, String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val chat by chatViewModel.chat.collectAsStateWithLifecycle()
    val messagesState by chatViewModel.messagesState.collectAsStateWithLifecycle()
    val replyingTo by chatViewModel.replyingTo.collectAsStateWithLifecycle()
    val editingMsg by chatViewModel.editingMessage.collectAsStateWithLifecycle()

    val voiceRecorder = remember { VoiceRecorderManager(context) }
    val voicePlayer = remember { VoicePlayerManager(context) }
    val isRecording by voiceRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingElapsed by voiceRecorder.elapsedMs.collectAsStateWithLifecycle()
    val playingMsgId by voicePlayer.playingMessageId.collectAsStateWithLifecycle()
    val playingProgress by voicePlayer.progress.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancelRecording()
            voicePlayer.stop()
            chatViewModel.onTypingChanged(false)
        }
    }

    LaunchedEffect(Unit) {
        chatViewModel.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMsgForMenu by remember { mutableStateOf<Message?>(null) }
    var forwardTargetMsg by remember { mutableStateOf<Message?>(null) }
    var fullScreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editingMsg) {
        if (editingMsg != null) {
            inputText = editingMsg!!.text
        }
    }

    val otherUid = chat?.memberUids?.firstOrNull { it != currentUid }
    val otherUser = otherUid?.let { usersById[it] }
    val isGroup = chat?.type == "group"
    val blockedUids = myPrivate?.blockedUids?.toSet().orEmpty()
    val isBlocked = otherUid != null && otherUid in blockedUids
    val isMuted = chat?.id in (myPrivate?.mutedChatIds?.toSet().orEmpty())

    val headerTitle = when {
        chat == null -> "Chat"
        isGroup -> chat!!.title
        otherUser != null -> otherUser.displayName
        else -> "Conversation"
    }
    val headerAvatar = if (isGroup) chat?.groupAvatarData else otherUser?.avatarData
    val typingOthers = chat?.typingUids?.filter { it != currentUid }.orEmpty()
    val headerSubtitle = when {
        typingOthers.isNotEmpty() -> {
            if (isGroup) "${usersById[typingOthers.first()]?.displayName ?: "Someone"} is typing..."
            else "typing..."
        }
        isGroup -> "${chat?.memberUids?.size ?: 0} members"
        otherUser == null -> ""
        otherUser.lastSeenPrivacy == "nobody" -> "@${otherUser.username}"
        otherUser.online -> "online"
        else -> TimeFormatters.formatLastSeen(otherUser.lastSeen)
    }

    // Launchers
    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording(scope)
                .onFailure { Toast.makeText(context, "Unable to start microphone", Toast.LENGTH_SHORT).show() }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice notes.", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            MediaUtils.compressImageUriToBase64(context, uri)
                .onSuccess { b64 ->
                    chatViewModel.sendMediaMessage(
                        senderName = myProfile.displayName,
                        type = "image",
                        caption = inputText.trim(),
                        mediaData = b64
                    )
                    inputText = ""
                }
                .onFailure { Toast.makeText(context, it.message ?: "Failed to attach image", Toast.LENGTH_SHORT).show() }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            MediaUtils.readUriToBase64(context, uri)
                .onSuccess { file ->
                    chatViewModel.sendMediaMessage(
                        senderName = myProfile.displayName,
                        type = "video",
                        caption = inputText.trim(),
                        mediaData = file.base64,
                        fileName = file.fileName,
                        fileMimeType = file.mimeType
                    )
                    inputText = ""
                }
                .onFailure { Toast.makeText(context, it.message ?: "Video exceeds 520 KB inline limit", Toast.LENGTH_LONG).show() }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            MediaUtils.readUriToBase64(context, uri)
                .onSuccess { file ->
                    chatViewModel.sendMediaMessage(
                        senderName = myProfile.displayName,
                        type = "document",
                        caption = inputText.trim(),
                        mediaData = file.base64,
                        fileName = file.fileName,
                        fileMimeType = file.mimeType
                    )
                    inputText = ""
                }
                .onFailure { Toast.makeText(context, it.message ?: "Document exceeds 520 KB inline limit", Toast.LENGTH_LONG).show() }
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatViewModel.markVisibleMessagesRead(messages, myProfile.readReceipts)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isGroup) {
                                chat?.id?.let { onOpenGroupInfo(it) }
                            }
                    ) {
                        AbidAvatar(
                            name = headerTitle,
                            avatarData = headerAvatar,
                            size = 40.dp,
                            isOnline = !isGroup && otherUser?.online == true && otherUser.lastSeenPrivacy != "nobody",
                            isGroup = isGroup
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (headerSubtitle.isNotBlank()) {
                                Text(
                                    text = headerSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (!isGroup && otherUser != null && !isBlocked) {
                        IconButton(
                            onClick = { onStartCall(otherUser, "audio") },
                            modifier = Modifier.testTag("chat_audio_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Audio call")
                        }
                        IconButton(
                            onClick = { onStartCall(otherUser, "video") },
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video call")
                        }
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search messages") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showSearchBar = !showSearchBar
                                }
                            )
                            if (isGroup && chat != null) {
                                DropdownMenuItem(
                                    text = { Text("Group info") },
                                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onOpenGroupInfo(chat!!.id)
                                    }
                                )
                            }
                            if (chat != null) {
                                DropdownMenuItem(
                                    text = { Text(if (isMuted) "Unmute" else "Mute notifications") },
                                    leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onToggleMuted(chat!!.id, isMuted)
                                    }
                                )
                            }
                            if (otherUser != null) {
                                DropdownMenuItem(
                                    text = { Text(if (isBlocked) "Unblock user" else "Block user") },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onToggleBlock(otherUser.uid, isBlocked)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report user") },
                                    leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        showReportDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAbidColors.current.chatCanvas)
                .padding(padding)
                .imePadding()
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search in conversation...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            searchQuery = ""
                            showSearchBar = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = (messagesState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        val shown = remember(messages, searchQuery) {
                            val q = searchQuery.trim().lowercase()
                            if (q.isBlank()) messages
                            else messages.filter {
                                it.text.lowercase().contains(q) ||
                                    it.fileName?.lowercase()?.contains(q) == true
                            }
                        }

                        if (shown.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ) {
                                    Text(
                                        text = if (searchQuery.isBlank()) "Say hello to start the conversation!" else "No matching messages.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(shown, key = { it.id }) { msg ->
                                    val isMine = msg.senderId == currentUid
                                    val memberCount = chat?.memberUids?.size ?: 2
                                    val otherMemberCount = (memberCount - 1).coerceAtLeast(1)
                                    val isRead = msg.readBy.count { it != currentUid } >= otherMemberCount
                                    val isDelivered = msg.deliveredTo.count { it != currentUid } >= 1

                                    MessageBubble(
                                        message = msg,
                                        isMine = isMine,
                                        isGroup = isGroup,
                                        isDelivered = isDelivered,
                                        isRead = isRead,
                                        isPlayingAudio = playingMsgId == msg.id,
                                        audioProgress = if (playingMsgId == msg.id) playingProgress else 0f,
                                        onPlayToggleVoice = {
                                            val data = msg.mediaData
                                            if (!data.isNullOrBlank()) {
                                                voicePlayer.togglePlay(msg.id, data, scope)
                                            }
                                        },
                                        onOpenImage = { fullScreenImageBase64 = msg.mediaData },
                                        onOpenAttachment = {
                                            val data = msg.mediaData
                                            if (!data.isNullOrBlank()) {
                                                MediaUtils.openBase64Attachment(
                                                    context,
                                                    data,
                                                    msg.fileName ?: "attachment",
                                                    msg.fileMimeType ?: "*/*"
                                                ).onFailure {
                                                    Toast.makeText(context, "No viewer app found for this file.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onLongPress = { selectedMsgForMenu = msg }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isBlocked) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "You blocked this contact.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { otherUser?.let { onToggleBlock(it.uid, true) } }) {
                            Text("Unblock")
                        }
                    }
                }
            } else {
                // Reply or Edit Banner
                val bannerTitle = when {
                    editingMsg != null -> "Editing message"
                    replyingTo != null -> "Replying to ${replyingTo!!.senderName}"
                    else -> null
                }
                if (bannerTitle != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(34.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bannerTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = editingMsg?.text ?: replyingTo?.text?.ifBlank { replyingTo?.type ?: "" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = {
                                if (editingMsg != null) inputText = ""
                                chatViewModel.setReplyTarget(null)
                                chatViewModel.setEditTarget(null)
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                    }
                }

                // Quick Emoji Picker Row
                AnimatedVisibility(visible = showEmojiBar) {
                    val emojis = listOf("😀", "😂", "❤️", "👍", "🙏", "🔥", "🎉", "😍", "🤝", "👏", "😢", "😮", "💯", "✨")
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(emojis) { emoji ->
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            inputText += emoji
                                            chatViewModel.onTypingChanged(inputText.isNotBlank())
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }

                // Composer Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isRecording) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                                    )
                                    Text(
                                        text = "Recording ${TimeFormatters.formatDurationMs(recordingElapsed)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(onClick = { voiceRecorder.cancelRecording() }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    voiceRecorder.stopAndEncode()
                                        .onSuccess { (b64, dur) ->
                                            chatViewModel.sendMediaMessage(
                                                senderName = myProfile.displayName,
                                                type = "voice",
                                                caption = "",
                                                mediaData = b64,
                                                durationMs = dur
                                            )
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.message ?: "Voice note failed", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .testTag("stop_and_send_voice_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice message", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { showEmojiBar = !showEmojiBar },
                                    modifier = Modifier.testTag("emoji_toggle_button")
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEmotions,
                                        contentDescription = "Emojis",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextField(
                                    value = inputText,
                                    onValueChange = {
                                        if (it.length <= 4000) {
                                            inputText = it
                                            chatViewModel.onTypingChanged(it.isNotBlank())
                                        }
                                    },
                                    placeholder = { Text("Message") },
                                    maxLines = 4,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f).testTag("message_input")
                                )
                                IconButton(
                                    onClick = { showAttachSheet = true },
                                    modifier = Modifier.testTag("attach_button")
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = "Attach file or media",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val hasText = inputText.trim().isNotEmpty()
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    if (hasText) {
                                        val payload = inputText
                                        inputText = ""
                                        showEmojiBar = false
                                        chatViewModel.sendTextOrSaveEdit(myProfile.displayName, payload)
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            voiceRecorder.startRecording(scope)
                                                .onFailure {
                                                    Toast.makeText(context, "Unable to start microphone", Toast.LENGTH_SHORT).show()
                                                }
                                        } else {
                                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                .testTag(if (hasText) "send_message_button" else "record_voice_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                    contentDescription = if (hasText) "Send message" else "Record voice note",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Share Media or Document", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachOption(Icons.Default.Image, "Photo") {
                        showAttachSheet = false
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    AttachOption(Icons.Default.Videocam, "Video") {
                        showAttachSheet = false
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                    AttachOption(Icons.Default.Description, "Document") {
                        showAttachSheet = false
                        docPicker.launch(arrayOf("*/*"))
                    }
                }
            }
        }
    }

    selectedMsgForMenu?.let { msg ->
        ModalBottomSheet(onDismissRequest = { selectedMsgForMenu = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                ListActionRow(Icons.AutoMirrored.Filled.Reply, "Reply") {
                    chatViewModel.setReplyTarget(msg)
                    selectedMsgForMenu = null
                }
                if (!msg.deletedForAll) {
                    ListActionRow(Icons.AutoMirrored.Filled.Forward, "Forward") {
                        forwardTargetMsg = msg
                        selectedMsgForMenu = null
                    }
                    if (msg.text.isNotBlank()) {
                        ListActionRow(Icons.Default.ContentCopy, "Copy text") {
                            clipboard.setText(AnnotatedString(msg.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            selectedMsgForMenu = null
                        }
                    }
                    if (msg.senderId == currentUid && msg.type == "text") {
                        ListActionRow(Icons.Default.Edit, "Edit message") {
                            chatViewModel.setEditTarget(msg)
                            selectedMsgForMenu = null
                        }
                    }
                    if (msg.senderId == currentUid) {
                        ListActionRow(Icons.Default.Delete, "Delete for everyone") {
                            chatViewModel.deleteMessageForAll(msg.id)
                            selectedMsgForMenu = null
                        }
                    }
                }
            }
        }
    }

    forwardTargetMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { forwardTargetMsg = null },
            title = { Text("Forward message to...") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(allChats, key = { it.id }) { target ->
                        val label = if (target.type == "group") target.title
                        else {
                            val oid = target.memberUids.firstOrNull { it != currentUid }
                            usersById[oid]?.displayName ?: "Chat"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onForwardMessage(target, msg)
                                    forwardTargetMsg = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AbidAvatar(label, null, size = 38.dp, isGroup = target.type == "group")
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { forwardTargetMsg = null }) { Text("Cancel") }
            }
        )
    }

    fullScreenImageBase64?.let { b64 ->
        val bmp = remember(b64) { MediaUtils.decodeBase64Bitmap(b64) }
        Dialog(onDismissRequest = { fullScreenImageBase64 = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.Black) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Full image",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    IconButton(
                        onClick = { fullScreenImageBase64 = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showReportDialog && otherUser != null) {
        ReportUserDialog(
            targetName = otherUser.displayName,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                showReportDialog = false
                onSubmitReport(otherUser.uid, chat?.id, reason, details)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    isGroup: Boolean,
    isDelivered: Boolean,
    isRead: Boolean,
    isPlayingAudio: Boolean,
    audioProgress: Float,
    onPlayToggleVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenAttachment: () -> Unit,
    onLongPress: () -> Unit
) {
    val abidColors = LocalAbidColors.current
    val bubbleColor = if (isMine) abidColors.outgoingBubble else abidColors.incomingBubble
    val textColor = if (isMine) abidColors.outgoingBubbleText else abidColors.incomingBubbleText

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = {
                        when (message.type) {
                            "image" -> if (!message.deletedForAll) onOpenImage()
                            "video", "document" -> if (!message.deletedForAll) onOpenAttachment()
                            "voice" -> if (!message.deletedForAll) onPlayToggleVoice()
                            else -> onLongPress()
                        }
                    },
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMine && isGroup) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (!message.forwardedFrom.isNullOrBlank() && !message.deletedForAll) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, modifier = Modifier.size(12.dp), tint = abidColors.mutedText)
                        Text("Forwarded", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = abidColors.mutedText)
                    }
                    Spacer(Modifier.height(2.dp))
                }

                if (!message.replyToMessageId.isNullOrBlank() && !message.deletedForAll) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = message.replyToSenderName ?: "Reply",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = message.replyToText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (message.deletedForAll) {
                    Text(
                        text = "This message was deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = abidColors.mutedText
                    )
                } else {
                    when (message.type) {
                        "image" -> {
                            val bmp = remember(message.mediaData) { MediaUtils.decodeBase64Bitmap(message.mediaData) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Photo message",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                            }
                        }
                        "video" -> {
                            Surface(
                                color = Color.Black.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(44.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(message.fileName ?: "Video clip • Tap to open", color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                        }
                        "voice" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.width(210.dp).padding(vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = onPlayToggleVoice,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingAudio) "Pause voice" else "Play voice",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { if (isPlayingAudio) audioProgress else 0f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = TimeFormatters.formatDurationMs(message.durationMs ?: 0L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = abidColors.mutedText
                                    )
                                }
                            }
                        }
                        "document" -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(message.fileName ?: "Document", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = abidColors.mutedText)
                                    }
                                }
                            }
                            if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.edited && !message.deletedForAll) {
                        Text("edited", style = MaterialTheme.typography.labelSmall, color = abidColors.mutedText)
                    }
                    Text(
                        text = TimeFormatters.formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = abidColors.mutedText
                    )
                    if (isMine) {
                        val tickIcon = if (isDelivered || isRead) Icons.Default.DoneAll else Icons.Default.Done
                        val tickTint = if (isRead) abidColors.readTick else abidColors.mutedText
                        Icon(
                            imageVector = tickIcon,
                            contentDescription = if (isRead) "Read" else if (isDelivered) "Delivered" else "Sent",
                            tint = tickTint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ListActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
