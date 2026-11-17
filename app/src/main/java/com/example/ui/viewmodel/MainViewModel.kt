package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.toFriendlyMessage
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.StatusUpdate
import com.example.data.model.UiState
import com.example.data.model.UserPrivate
import com.example.data.model.UserPublic
import com.example.data.repository.AuthAndProfileRepository
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.StatusRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeSnapshot(
    val myPublic: UserPublic?,
    val myPrivate: UserPrivate?,
    val allUsers: List<UserPublic>,
    val chats: List<Chat>,
    val statuses: List<StatusUpdate>,
    val calls: List<CallSession>
)

class MainViewModel(
    val currentUserId: String,
    private val authRepo: AuthAndProfileRepository,
    private val chatRepo: ChatRepository,
    private val statusRepo: StatusRepository,
    private val callRepo: CallRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _usernameAvailable = MutableStateFlow<Boolean?>(null)
    val usernameAvailable: StateFlow<Boolean?> = _usernameAvailable.asStateFlow()

    val myPublicProfile: StateFlow<UiState<UserPublic?>> =
        authRepo.observeMyPublicProfile(currentUserId)
            .map<UserPublic?, UiState<UserPublic?>> { UiState.Success(it) }
            .catch { error ->
                val fe = unwrapFirestoreError(error)
                if (fe == null && error is CancellationException) throw error
                emit(UiState.Error((fe ?: error).toFriendlyMessage("Unable to load profile")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myPrivateProfile: StateFlow<UserPrivate?> =
        authRepo.observeMyPrivateProfile(currentUserId)
            .catch { error ->
                val fe = unwrapFirestoreError(error)
                if (fe == null && error is CancellationException) throw error
                Log.w("MainVM", "Private profile error", fe ?: error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allUsers: StateFlow<List<UserPublic>> =
        authRepo.observeAllPublicUsers(currentUserId)
            .catch { error ->
                val fe = unwrapFirestoreError(error)
                if (fe == null && error is CancellationException) throw error
                Log.w("MainVM", "Users list error", fe ?: error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chatsState: StateFlow<UiState<List<Chat>>> =
        chatRepo.observeMyChats(currentUserId)
            .map<List<Chat>, UiState<List<Chat>>> { UiState.Success(it) }
            .catch { error ->
                val fe = unwrapFirestoreError(error)
                if (fe == null && error is CancellationException) throw error
                emit(UiState.Error((fe ?: error).toFriendlyMessage("Unable to load chats")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val statusesState: StateFlow<UiState<List<StatusUpdate>>> =
        statusRepo.observeActiveStatuses(currentUserId)
            .map<List<StatusUpdate>, UiState<List<StatusUpdate>>> { UiState.Success(it) }
            .catch { error ->
                val fe = unwrapFirestoreError(error)
                if (fe == null && error is CancellationException) throw error
                emit(UiState.Error((fe ?: error).toFriendlyMessage("Unable to load statuses")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val callsState: StateFlow<UiState<List<CallSession>>> =
        callRepo.observeMyCalls(currentUserId)
            .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
            .catch { error ->
                val fe = unwrapFirestoreError(error)
                if (fe == null && error is CancellationException) throw error
                emit(UiState.Error((fe ?: error).toFriendlyMessage("Unable to load call history")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val incomingRingingCall: StateFlow<CallSession?> =
        callsState
            .map { state ->
                val list = (state as? UiState.Success)?.data.orEmpty()
                list.firstOrNull { call ->
                    call.calleeId == currentUserId && call.status == "ringing"
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val themeMode: StateFlow<String> =
        myPrivateProfile
            .map { it?.themeMode ?: "system" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "system")

    val usersById: StateFlow<Map<String, UserPublic>> =
        allUsers
            .map { list -> list.associateBy { it.uid } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val filteredStatuses: StateFlow<List<StatusUpdate>> =
        combine(statusesState, myPrivateProfile, chatsState, usersById) { statusState, priv, chatState, usersMap ->
            val raw = (statusState as? UiState.Success)?.data.orEmpty()
            val blocked = priv?.blockedUids?.toSet().orEmpty()
            val chats = (chatState as? UiState.Success)?.data.orEmpty()
            val contactUids = chats.flatMap { it.memberUids }.toSet()
            raw.filter { status ->
                if (status.userId == currentUserId) return@filter true
                if (status.userId in blocked) return@filter false
                val author = usersMap[status.userId]
                when (author?.statusPrivacy ?: status.privacy) {
                    "nobody" -> false
                    "contacts" -> status.userId in contactUids
                    else -> status.privacy == "everyone" || status.userId in contactUids
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun checkUsername(username: String) {
        viewModelScope.launch {
            val clean = username.trim().lowercase()
            if (!clean.matches(Regex("^[a-z0-9_]{3,24}$"))) {
                _usernameAvailable.value = false
                return@launch
            }
            authRepo.isUsernameAvailable(clean)
                .onSuccess { _usernameAvailable.value = it }
                .onFailure { _usernameAvailable.value = null }
        }
    }

    fun createInitialProfile(
        username: String,
        displayName: String,
        about: String,
        avatarData: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            authRepo.createInitialProfile(username, displayName, about, avatarData)
                .onSuccess {
                    _events.tryEmit("Welcome to ABID Messenger!")
                    onDone()
                }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not save profile")) }
            _isBusy.value = false
        }
    }

    fun updateProfile(
        current: UserPublic,
        username: String,
        displayName: String,
        about: String,
        avatarData: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            authRepo.updateProfile(current, username, displayName, about, avatarData)
                .onSuccess {
                    _events.tryEmit("Profile updated")
                    onDone()
                }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not update profile")) }
            _isBusy.value = false
        }
    }

    fun setPresence(online: Boolean) {
        viewModelScope.launch {
            authRepo.updatePresence(online)
        }
    }

    fun updatePrivacy(lastSeen: String, statusPrivacy: String, readReceipts: Boolean) {
        viewModelScope.launch {
            authRepo.updatePrivacySettings(lastSeen, statusPrivacy, readReceipts)
                .onSuccess { _events.tryEmit("Privacy settings updated") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Failed to update privacy")) }
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            authRepo.updateThemeMode(mode)
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Failed to update theme")) }
        }
    }

    fun openDirectChat(otherUser: UserPublic, onChatReady: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            chatRepo.getOrCreateDirectChat(otherUser)
                .onSuccess { chatId -> onChatReady(chatId) }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not start chat")) }
            _isBusy.value = false
        }
    }

    fun createGroup(
        title: String,
        description: String,
        memberUids: List<String>,
        avatarData: String?,
        onGroupCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            chatRepo.createGroupChat(title, description, memberUids, avatarData)
                .onSuccess { chatId ->
                    _events.tryEmit("Group \"$title\" created")
                    onGroupCreated(chatId)
                }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not create group")) }
            _isBusy.value = false
        }
    }

    fun togglePinned(chatId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            authRepo.toggleChatListFlag("pinnedChatIds", chatId, currentlyPinned)
                .onSuccess { _events.tryEmit(if (currentlyPinned) "Chat unpinned" else "Chat pinned") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage()) }
        }
    }

    fun toggleArchived(chatId: String, currentlyArchived: Boolean) {
        viewModelScope.launch {
            authRepo.toggleChatListFlag("archivedChatIds", chatId, currentlyArchived)
                .onSuccess { _events.tryEmit(if (currentlyArchived) "Chat unarchived" else "Chat archived") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage()) }
        }
    }

    fun toggleMuted(chatId: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            authRepo.toggleChatListFlag("mutedChatIds", chatId, currentlyMuted)
                .onSuccess { _events.tryEmit(if (currentlyMuted) "Notifications unmuted" else "Chat muted") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage()) }
        }
    }

    fun toggleBlockUser(targetUid: String, currentlyBlocked: Boolean) {
        viewModelScope.launch {
            authRepo.toggleBlockUser(targetUid, currentlyBlocked)
                .onSuccess { _events.tryEmit(if (currentlyBlocked) "User unblocked" else "User blocked") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage()) }
        }
    }

    fun submitReport(reportedUserId: String, chatId: String?, reason: String, details: String) {
        viewModelScope.launch {
            authRepo.submitReport(reportedUserId, chatId, reason, details)
                .onSuccess { _events.tryEmit("Report submitted. Thank you for keeping ABID Messenger safe.") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not submit report")) }
        }
    }

    fun forwardMessageToChat(targetChat: Chat, original: Message) {
        val myName = (myPublicProfile.value as? UiState.Success)?.data?.displayName ?: "Me"
        viewModelScope.launch {
            chatRepo.sendMessage(
                chat = targetChat,
                senderName = myName,
                type = original.type,
                textRaw = original.text,
                mediaData = original.mediaData,
                fileName = original.fileName,
                fileMimeType = original.fileMimeType,
                durationMs = original.durationMs,
                forwardedFrom = original.senderName
            ).onSuccess {
                _events.tryEmit("Message forwarded")
            }.onFailure {
                _events.tryEmit(it.toFriendlyMessage("Could not forward message"))
            }
        }
    }

    fun postStatus(
        type: String,
        text: String,
        backgroundHex: String,
        mediaData: String?,
        privacy: String,
        onDone: () -> Unit = {}
    ) {
        val profile = (myPublicProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            statusRepo.postStatus(
                authorName = profile.displayName,
                authorAvatar = profile.avatarData,
                type = type,
                textRaw = text,
                backgroundHex = backgroundHex,
                mediaData = mediaData,
                privacy = privacy
            ).onSuccess {
                _events.tryEmit("Status posted (visible for 24h)")
                onDone()
            }.onFailure {
                _events.tryEmit(it.toFriendlyMessage("Could not post status"))
            }
            _isBusy.value = false
        }
    }

    fun markStatusViewed(statusId: String) {
        viewModelScope.launch {
            statusRepo.markStatusViewed(statusId)
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            statusRepo.deleteStatus(statusId)
                .onSuccess { _events.tryEmit("Status deleted") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Failed to delete status")) }
        }
    }

    fun initiateCall(otherUser: UserPublic, callType: String, onCallCreated: (String) -> Unit) {
        val me = (myPublicProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            callRepo.createCall(
                callerName = me.displayName,
                callerAvatar = me.avatarData,
                calleeId = otherUser.uid,
                calleeName = otherUser.displayName,
                calleeAvatar = otherUser.avatarData,
                callType = callType
            ).onSuccess { callId ->
                onCallCreated(callId)
            }.onFailure {
                _events.tryEmit(it.toFriendlyMessage("Could not start call"))
            }
            _isBusy.value = false
        }
    }

    fun rejectIncomingCall(callId: String) {
        viewModelScope.launch {
            callRepo.updateCallStatus(callId, "rejected", 0L)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepo.signOut(context)
        }
    }

    fun deleteAccount(context: Context, onDeleted: () -> Unit) {
        val username = (myPublicProfile.value as? UiState.Success)?.data?.username.orEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            authRepo.deleteAccount(context, username)
                .onSuccess { onDeleted() }
                .onFailure {
                    _events.tryEmit(
                        it.toFriendlyMessage("Please sign out and sign back in recently before deleting your account.")
                    )
                }
            _isBusy.value = false
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    companion object {
        fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MainViewModel(
                    currentUserId = currentUserId,
                    authRepo = AuthAndProfileRepository(db),
                    chatRepo = ChatRepository(db),
                    statusRepo = StatusRepository(db),
                    callRepo = CallRepository(db)
                )
            }
        }
    }
}
