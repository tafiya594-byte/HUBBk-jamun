package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.UserPublic
import com.example.ui.theme.LocalAbidColors
import com.example.util.MediaUtils

@Composable
fun AbidAvatar(
    name: String,
    avatarData: String?,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    hasStatusRing: Boolean = false,
    isGroup: Boolean = false
) {
    val bitmap = remember(avatarData) { MediaUtils.decodeBase64Bitmap(avatarData) }
    val ringColor = if (hasStatusRing) MaterialTheme.colorScheme.primary else Color.Transparent
    val initials = remember(name) {
        name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { if (isGroup) "G" else "A" }
    }

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hasStatusRing) Modifier.border(2.5.dp, ringColor, CircleShape).padding(3.dp)
                    else Modifier
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "$name avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(LocalAbidColors.current.onlineDot)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("empty_state_action_button")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ReportUserDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf("Spam", "Harassment", "Impersonation", "Inappropriate Content")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Select a reason. Blocking and reporting helps protect ABID Messenger users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason) },
                            leadingIcon = if (selectedReason == reason) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    label = { Text("Additional details (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatAndGroupSheet(
    currentUid: String,
    allUsers: List<UserPublic>,
    blockedUids: Set<String>,
    onDismiss: () -> Unit,
    onStartDirectChat: (UserPublic) -> Unit,
    onCreateGroup: (title: String, description: String, memberUids: List<String>, avatarData: String?) -> Unit
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }
    var groupAvatar by remember { mutableStateOf<String?>(null) }
    val selectedUids = remember { mutableStateListOf<String>() }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            MediaUtils.compressImageUriToBase64(context, uri, maxDimension = 512, maxBytes = 140_000)
                .onSuccess { groupAvatar = it }
        }
    }

    val candidates = remember(allUsers, currentUid, blockedUids, search) {
        val q = search.trim().lowercase()
        allUsers.filter { u ->
            u.uid != currentUid && u.uid !in blockedUids && (
                q.isBlank() ||
                    u.displayName.lowercase().contains(q) ||
                    u.username.lowercase().contains(q)
                )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Direct Chat") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("New Group") }
                )
            }

            if (tabIndex == 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (groupAvatar != null) {
                                AbidAvatar(name = groupTitle.ifBlank { "G" }, avatarData = groupAvatar, size = 56.dp)
                            } else {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Pick group icon", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { if (it.length <= 80) groupTitle = it },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("group_name_input")
                    )
                }
                OutlinedTextField(
                    value = groupDesc,
                    onValueChange = { if (it.length <= 250) groupDesc = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search by name or @username") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().testTag("user_search_input")
            )

            if (candidates.isEmpty()) {
                Text(
                    text = "No registered users match your search yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(candidates, key = { it.uid }) { user ->
                        val isSelected = user.uid in selectedUids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (tabIndex == 0) {
                                        onStartDirectChat(user)
                                    } else {
                                        if (isSelected) selectedUids.remove(user.uid) else selectedUids.add(user.uid)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AbidAvatar(
                                name = user.displayName,
                                avatarData = user.avatarData,
                                size = 44.dp,
                                isOnline = user.online
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${user.username} • ${user.about}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (tabIndex == 1) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (user.uid !in selectedUids) selectedUids.add(user.uid)
                                        } else {
                                            selectedUids.remove(user.uid)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (tabIndex == 1) {
                Button(
                    onClick = {
                        onCreateGroup(groupTitle.trim(), groupDesc.trim(), selectedUids.toList(), groupAvatar)
                    },
                    enabled = groupTitle.trim().isNotEmpty() && selectedUids.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_group_button")
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Group (${selectedUids.size} selected)")
                }
            }
        }
    }
}
