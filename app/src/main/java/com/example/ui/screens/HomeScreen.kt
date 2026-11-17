package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.StatusUpdate
import com.example.data.model.UiState
import com.example.data.model.UserPrivate
import com.example.data.model.UserPublic
import com.example.ui.components.AbidAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NewChatAndGroupSheet
import com.example.ui.components.ReportUserDialog
import com.example.ui.theme.LocalAbidColors
import com.example.util.MediaUtils
import com.example.util.TimeFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUid: String,
    myProfile: UserPublic,
    myPrivate: UserPrivate?,
    allUsers: List<UserPublic>,
    usersById: Map<String, UserPublic>,
    chatsState: UiState<List<Chat>>,
    statuses: List<StatusUpdate>,
    callsState: UiState<List<CallSession>>,
    onOpenChat: (String) -> Unit,
    onStartDirectChat: (UserPublic) -> Unit,
    onCreateGroup: (String, String, List<String>, String?) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onToggleArchived: (String, Boolean) -> Unit,
    onToggleMuted: (String, Boolean) -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onSubmitReport: (String, String?, String, String) -> Unit,
    onOpenStatusViewer: (String) -> Unit,
    onPostStatus: (type: String, text: String, bgHex: String, mediaData: String?, privacy: String) -> Unit,
    onStartCall: (UserPublic, String) -> Unit,
    onUpdateProfile: (UserPublic, String, String, String, String?) -> Unit,
    onUpdatePrivacy: (String, String, Boolean) -> Unit,
    onUpdateThemeMode: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showStatusComposer by remember { mutableStateOf(false) }
    var statusInitialTab by remember { mutableIntStateOf(0) }
    var reportTarget by remember { mutableStateOf<Pair<UserPublic, String?>?>(null) }

    val blockedUids = remember(myPrivate) { myPrivate?.blockedUids?.toSet().orEmpty() }
    val pinnedIds = remember(myPrivate) { myPrivate?.pinnedChatIds?.toSet().orEmpty() }
    val archivedIds = remember(myPrivate) { myPrivate?.archivedChatIds?.toSet().orEmpty() }
    val mutedIds = remember(myPrivate) { myPrivate?.mutedChatIds?.toSet().orEmpty() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = "Status") },
                        label = { Text("Status") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                        label = { Text("Calls") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "ABID Messenger"
                                        1 -> "Status"
                                        2 -> "Calls"
                                        else -> "Settings"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedTab == 0) {
                                    Text(
                                        text = "@${myProfile.username}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        actions = {
                            if (selectedTab == 0) {
                                IconButton(
                                    onClick = { showNewChatSheet = true },
                                    modifier = Modifier.testTag("top_new_chat_button")
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = "New chat or group")
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                                label = { Text("Chats") },
                                modifier = Modifier.testTag("nav_chats")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = "Status") },
                                label = { Text("Status") },
                                modifier = Modifier.testTag("nav_status")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                                label = { Text("Calls") },
                                modifier = Modifier.testTag("nav_calls")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_settings")
                            )
                        }
                    }
                },
                floatingActionButton = {
                    when (selectedTab) {
                        0 -> FloatingActionButton(
                            onClick = { showNewChatSheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_chat")
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = "New message")
                        }
                        1 -> FloatingActionButton(
                            onClick = {
                                statusInitialTab = 0
                                showStatusComposer = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_status")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Add status")
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (selectedTab) {
                        0 -> ChatsTabContent(
                            currentUid = currentUid,
                            chatsState = chatsState,
                            allUsers = allUsers,
                            usersById = usersById,
                            blockedUids = blockedUids,
                            pinnedIds = pinnedIds,
                            archivedIds = archivedIds,
                            mutedIds = mutedIds,
                            onOpenChat = onOpenChat,
                            onStartDirectChat = onStartDirectChat,
                            onTogglePinned = onTogglePinned,
                            onToggleArchived = onToggleArchived,
                            onToggleMuted = onToggleMuted,
                            onToggleBlock = onToggleBlock,
                            onRequestReport = { user, chatId -> reportTarget = user to chatId },
                            onOpenNewChat = { showNewChatSheet = true }
                        )
                        1 -> StatusTabContent(
                            currentUid = currentUid,
                            myProfile = myProfile,
                            statuses = statuses,
                            usersById = usersById,
                            onAddTextStatus = {
                                statusInitialTab = 0
                                showStatusComposer = true
                            },
                            onAddMediaStatus = {
                                statusInitialTab = 1
                                showStatusComposer = true
                            },
                            onOpenStatusViewer = onOpenStatusViewer
                        )
                        2 -> CallsTabContent(
                            currentUid = currentUid,
                            callsState = callsState,
                            usersById = usersById,
                            onStartCall = onStartCall
                        )
                        3 -> SettingsTabContent(
                            myProfile = myProfile,
                            myPrivate = myPrivate,
                            allUsers = allUsers,
                            usersById = usersById,
                            onUpdateProfile = onUpdateProfile,
                            onUpdatePrivacy = onUpdatePrivacy,
                            onUpdateThemeMode = onUpdateThemeMode,
                            onUnblockUser = { uid -> onToggleBlock(uid, true) },
                            onSignOut = onSignOut,
                            onDeleteAccount = onDeleteAccount
                        )
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        NewChatAndGroupSheet(
            currentUid = currentUid,
            allUsers = allUsers,
            blockedUids = blockedUids,
            onDismiss = { showNewChatSheet = false },
            onStartDirectChat = { user ->
                showNewChatSheet = false
                onStartDirectChat(user)
            },
            onCreateGroup = { title, desc, members, avatar ->
                showNewChatSheet = false
                onCreateGroup(title, desc, members, avatar)
            }
        )
    }

    if (showStatusComposer) {
        StatusComposerDialog(
            initialTab = statusInitialTab,
            defaultPrivacy = if (myProfile.statusPrivacy == "contacts") "contacts" else "everyone",
            onDismiss = { showStatusComposer = false },
            onPost = { type, text, bgHex, mediaData, privacy ->
                showStatusComposer = false
                onPostStatus(type, text, bgHex, mediaData, privacy)
            }
        )
    }

    reportTarget?.let { (user, chatId) ->
        ReportUserDialog(
            targetName = user.displayName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                reportTarget = null
                onSubmitReport(user.uid, chatId, reason, details)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatsTabContent(
    currentUid: String,
    chatsState: UiState<List<Chat>>,
    allUsers: List<UserPublic>,
    usersById: Map<String, UserPublic>,
    blockedUids: Set<String>,
    pinnedIds: Set<String>,
    archivedIds: Set<String>,
    mutedIds: Set<String>,
    onOpenChat: (String) -> Unit,
    onStartDirectChat: (UserPublic) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onToggleArchived: (String, Boolean) -> Unit,
    onToggleMuted: (String, Boolean) -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onRequestReport: (UserPublic, String?) -> Unit,
    onOpenNewChat: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search chats or @username...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("chats_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Pinned", "Archived").forEach { chip ->
                FilterChip(
                    selected = filter == chip,
                    onClick = { filter = chip },
                    label = { Text(chip) }
                )
            }
        }

        when (chatsState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Couldn't load conversations",
                    subtitle = chatsState.message
                )
            }
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val visibleChats = remember(chatsState.data, usersById, pinnedIds, archivedIds, filter, q) {
                    chatsState.data
                        .filter { chat ->
                            val isArchived = chat.id in archivedIds
                            when (filter) {
                                "Archived" -> isArchived
                                "Pinned" -> chat.id in pinnedIds && !isArchived
                                else -> !isArchived
                            }
                        }
                        .filter { chat ->
                            if (q.isBlank()) return@filter true
                            val otherUid = chat.memberUids.firstOrNull { it != currentUid }
                            val otherUser = otherUid?.let { usersById[it] }
                            val chatName = if (chat.type == "group") chat.title else otherUser?.displayName.orEmpty()
                            chatName.lowercase().contains(q) ||
                                otherUser?.username?.lowercase()?.contains(q) == true ||
                                chat.lastMessageText.lowercase().contains(q)
                        }
                        .sortedWith(
                            compareByDescending<Chat> { it.id in pinnedIds }
                                .thenByDescending { it.lastMessageAt?.seconds ?: 0L }
                        )
                }

                val matchingUsers = remember(allUsers, q, blockedUids) {
                    if (q.isBlank()) emptyList()
                    else allUsers.filter {
                        it.uid != currentUid && it.uid !in blockedUids &&
                            (it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q))
                    }
                }

                if (visibleChats.isEmpty() && matchingUsers.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = if (filter == "Archived") "No archived chats" else "No conversations yet",
                        subtitle = "Tap the button below to search users by @username or create a group.",
                        actionLabel = if (filter == "All") "Start a Chat" else null,
                        onAction = if (filter == "All") onOpenNewChat else null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(visibleChats, key = { it.id }) { chat ->
                            val otherUid = chat.memberUids.firstOrNull { it != currentUid }
                            val otherUser = otherUid?.let { usersById[it] }
                            val displayTitle = when {
                                chat.type == "group" -> chat.title
                                otherUser != null -> otherUser.displayName
                                else -> "Conversation"
                            }
                            val avatar = if (chat.type == "group") chat.groupAvatarData else otherUser?.avatarData
                            val isOnline = chat.type == "direct" && otherUser?.online == true && otherUser.lastSeenPrivacy != "nobody"
                            val isTyping = chat.typingUids.any { it != currentUid }
                            val isPinned = chat.id in pinnedIds
                            val isArchived = chat.id in archivedIds
                            val isMuted = chat.id in mutedIds
                            val isBlocked = otherUid != null && otherUid in blockedUids

                            var menuExpanded by remember { mutableStateOf(false) }

                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onOpenChat(chat.id) },
                                            onLongClick = { menuExpanded = true }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("chat_row_${chat.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AbidAvatar(
                                        name = displayTitle,
                                        avatarData = avatar,
                                        size = 54.dp,
                                        isOnline = isOnline,
                                        isGroup = chat.type == "group"
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = TimeFormatters.formatChatListTime(chat.lastMessageAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LocalAbidColors.current.mutedText
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (chat.lastMessageSenderId == currentUid && chat.lastMessageType != "none") {
                                                    Icon(
                                                        imageVector = Icons.Default.DoneAll,
                                                        contentDescription = "Sent",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                    )
                                                }
                                                Text(
                                                    text = when {
                                                        isTyping -> "typing..."
                                                        chat.lastMessageText.isNotBlank() -> chat.lastMessageText
                                                        else -> "Tap to send a message"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (isMuted) {
                                                    Icon(
                                                        Icons.Default.NotificationsOff,
                                                        contentDescription = "Muted",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = LocalAbidColors.current.mutedText
                                                    )
                                                }
                                                if (isPinned) {
                                                    Icon(
                                                        Icons.Default.PushPin,
                                                        contentDescription = "Pinned",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Chat actions")
                                    }
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isPinned) "Unpin chat" else "Pin chat") },
                                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onTogglePinned(chat.id, isPinned)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isMuted) "Unmute notifications" else "Mute notifications") },
                                        leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleMuted(chat.id, isMuted)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isArchived) "Unarchive chat" else "Archive chat") },
                                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleArchived(chat.id, isArchived)
                                        }
                                    )
                                    if (otherUser != null) {
                                        DropdownMenuItem(
                                            text = { Text(if (isBlocked) "Unblock user" else "Block user") },
                                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                onToggleBlock(otherUser.uid, isBlocked)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Report user") },
                                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                onRequestReport(otherUser, chat.id)
                                            }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }

                        if (matchingUsers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "People on ABID Messenger",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                            items(matchingUsers, key = { "user_${it.uid}" }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStartDirectChat(user) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AbidAvatar(user.displayName, user.avatarData, size = 46.dp, isOnline = user.online)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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
private fun StatusTabContent(
    currentUid: String,
    myProfile: UserPublic,
    statuses: List<StatusUpdate>,
    usersById: Map<String, UserPublic>,
    onAddTextStatus: () -> Unit,
    onAddMediaStatus: () -> Unit,
    onOpenStatusViewer: (String) -> Unit
) {
    val myStatuses = remember(statuses, currentUid) { statuses.filter { it.userId == currentUid } }
    val othersGrouped = remember(statuses, currentUid) {
        statuses.filter { it.userId != currentUid }.groupBy { it.userId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myStatuses.isNotEmpty()) onOpenStatusViewer(currentUid) else onAddTextStatus()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AbidAvatar(
                            name = myProfile.displayName,
                            avatarData = myProfile.avatarData,
                            size = 56.dp,
                            hasStatusRing = myStatuses.isNotEmpty()
                        )
                        if (myStatuses.isEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add status",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (myStatuses.isEmpty()) "Tap to add a 24-hour status update"
                            else "${myStatuses.size} active update(s) • Tap to view & see viewers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddTextStatus,
                        modifier = Modifier.weight(1f).testTag("add_text_status_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Text Status")
                    }
                    Button(
                        onClick = onAddMediaStatus,
                        modifier = Modifier.weight(1f).testTag("add_media_status_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Photo / Video")
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Updates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (othersGrouped.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.RadioButtonChecked,
                    title = "No recent updates",
                    subtitle = "When contacts post a photo, video, or text status, they'll appear here for 24 hours."
                )
            }
        } else {
            items(othersGrouped.entries.toList(), key = { it.key }) { (ownerUid, list) ->
                val user = usersById[ownerUid]
                val latest = list.first()
                val hasUnseen = list.any { currentUid !in it.viewerUids }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenStatusViewer(ownerUid) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AbidAvatar(
                        name = user?.displayName ?: latest.authorName,
                        avatarData = user?.avatarData ?: latest.authorAvatar,
                        size = 54.dp,
                        hasStatusRing = hasUnseen
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.displayName ?: latest.authorName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${list.size} update(s) • ${TimeFormatters.formatChatListTime(latest.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsTabContent(
    currentUid: String,
    callsState: UiState<List<CallSession>>,
    usersById: Map<String, UserPublic>,
    onStartCall: (UserPublic, String) -> Unit
) {
    when (callsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> EmptyStateCard(
            icon = Icons.Default.Call,
            title = "Unable to load call history",
            subtitle = callsState.message
        )
        is UiState.Success -> {
            if (callsState.data.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.Call,
                    title = "No call history",
                    subtitle = "Start a real-time WebRTC audio or video call from any conversation.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(callsState.data, key = { it.id }) { call ->
                        val isOutgoing = call.callerId == currentUid
                        val otherUid = if (isOutgoing) call.calleeId else call.callerId
                        val otherUser = usersById[otherUid]
                        val displayName = otherUser?.displayName ?: if (isOutgoing) call.calleeName else call.callerName
                        val avatar = otherUser?.avatarData ?: if (isOutgoing) call.calleeAvatar else call.callerAvatar
                        val isMissed = !isOutgoing && call.status in listOf("missed", "rejected")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AbidAvatar(name = displayName, avatarData = avatar, size = 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val dirIcon = when {
                                        isMissed -> Icons.AutoMirrored.Filled.CallMissed
                                        isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                                        else -> Icons.AutoMirrored.Filled.CallReceived
                                    }
                                    Icon(
                                        imageVector = dirIcon,
                                        contentDescription = null,
                                        tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val dur = if (call.durationSeconds > 0) " (${TimeFormatters.formatDurationSeconds(call.durationSeconds)})" else ""
                                    Text(
                                        text = "${TimeFormatters.formatChatListTime(call.createdAt)} • ${call.status}$dur",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (otherUser != null) {
                                IconButton(
                                    onClick = { onStartCall(otherUser, call.callType) },
                                    modifier = Modifier.testTag("redial_${call.id}")
                                ) {
                                    Icon(
                                        imageVector = if (call.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                        contentDescription = "Call back",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
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
private fun SettingsTabContent(
    myProfile: UserPublic,
    myPrivate: UserPrivate?,
    allUsers: List<UserPublic>,
    usersById: Map<String, UserPublic>,
    onUpdateProfile: (UserPublic, String, String, String, String?) -> Unit,
    onUpdatePrivacy: (String, String, Boolean) -> Unit,
    onUpdateThemeMode: (String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    var showEditProfile by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val themeMode = myPrivate?.themeMode ?: "system"
    val blockedList = myPrivate?.blockedUids.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditProfile = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AbidAvatar(myProfile.displayName, myProfile.avatarData, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(myProfile.displayName, style = MaterialTheme.typography.titleLarge)
                    Text("@${myProfile.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        myProfile.about,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                IconButton(onClick = { showEditProfile = true }, modifier = Modifier.testTag("edit_profile_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit profile")
                }
            }
        }

        // Theme Mode
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                        FilterChip(
                            selected = themeMode == key,
                            onClick = { onUpdateThemeMode(key) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("theme_$key")
                        )
                    }
                }
            }
        }

        // Privacy Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
                }

                Text("Last Seen & Online", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("everyone" to "Everyone", "nobody" to "Nobody").forEach { (key, label) ->
                        FilterChip(
                            selected = myProfile.lastSeenPrivacy == key,
                            onClick = { onUpdatePrivacy(key, myProfile.statusPrivacy, myProfile.readReceipts) },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Status Privacy", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("everyone" to "Everyone", "contacts" to "Contacts", "nobody" to "Nobody").forEach { (key, label) ->
                        FilterChip(
                            selected = myProfile.statusPrivacy == key,
                            onClick = { onUpdatePrivacy(myProfile.lastSeenPrivacy, key, myProfile.readReceipts) },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Read Receipts", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Show blue checkmarks when you read messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = myProfile.readReceipts,
                        onCheckedChange = { enabled ->
                            onUpdatePrivacy(myProfile.lastSeenPrivacy, myProfile.statusPrivacy, enabled)
                        },
                        modifier = Modifier.testTag("read_receipts_switch")
                    )
                }

                OutlinedButton(
                    onClick = { showBlockedDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Block, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Blocked Users (${blockedList.size})")
                }
            }
        }

        // Account Actions
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign Out")
        }

        TextButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().testTag("delete_account_button")
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Delete Account")
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showEditProfile) {
        var editDisplayName by remember { mutableStateOf(myProfile.displayName) }
        var editUsername by remember { mutableStateOf(myProfile.username) }
        var editAbout by remember { mutableStateOf(myProfile.about) }
        var editAvatar by remember { mutableStateOf(myProfile.avatarData) }

        val avatarPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                MediaUtils.compressImageUriToBase64(context, uri, maxDimension = 512, maxBytes = 140_000)
                    .onSuccess { editAvatar = it }
            }
        }

        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.clickable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        AbidAvatar(editDisplayName.ifBlank { "A" }, editAvatar, size = 72.dp)
                    }
                    TextButton(onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Change Photo")
                    }
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { if (it.length <= 50) editDisplayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { input ->
                            val clean = input.lowercase().filter { it.isLetterOrDigit() || it == '_' }
                            if (clean.length <= 24) editUsername = clean
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAbout,
                        onValueChange = { if (it.length <= 160) editAbout = it },
                        label = { Text("About") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditProfile = false
                        onUpdateProfile(myProfile, editUsername, editDisplayName, editAbout, editAvatar)
                    },
                    enabled = editDisplayName.trim().isNotEmpty() && editUsername.matches(Regex("^[a-z0-9_]{3,24}$"))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) { Text("Cancel") }
            }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Users") },
            text = {
                if (blockedList.isEmpty()) {
                    Text("You haven't blocked anyone.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blockedList.forEach { uid ->
                            val user = usersById[uid]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(user?.displayName ?: uid.take(12))
                                TextButton(onClick = { onUnblockUser(uid) }) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("Done") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = {
                Text("This permanently deletes your ABID Messenger profile, @${myProfile.username} handle, and personal preferences. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
