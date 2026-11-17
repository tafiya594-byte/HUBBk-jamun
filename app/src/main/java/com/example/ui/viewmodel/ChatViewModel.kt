package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.toFriendlyMessage
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.UiState
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatId: String,
    private val currentUserId: String,
    private val chatRepo: ChatRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo: StateFlow<Message?> = _replyingTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    private var typingStopJob: Job? = null

    val chat: StateFlow<Chat?> = chatRepo.observeChat(chatId, currentUserId)
        .catch { error ->
            val fe = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fe == null && error is CancellationException) throw error
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val messagesState: StateFlow<UiState<List<Message>>> =
        chatRepo.observeMessages(chatId, currentUserId)
            .map<List<Message>, UiState<List<Message>>> { UiState.Success(it) }
            .catch { error ->
                val fe = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fe == null && error is CancellationException) throw error
                emit(UiState.Error((fe ?: error).toFriendlyMessage("Unable to load messages")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun setReplyTarget(message: Message?) {
        _editingMessage.value = null
        _replyingTo.value = message
    }

    fun setEditTarget(message: Message?) {
        _replyingTo.value = null
        _editingMessage.value = message
    }

    fun onTypingChanged(hasText: Boolean) {
        typingStopJob?.cancel()
        if (!hasText) {
            viewModelScope.launch { chatRepo.setTyping(chatId, false) }
            return
        }
        viewModelScope.launch { chatRepo.setTyping(chatId, true) }
        typingStopJob = viewModelScope.launch {
            delay(2800L)
            chatRepo.setTyping(chatId, false)
        }
    }

    fun sendTextOrSaveEdit(senderName: String, text: String) {
        val currentEdit = _editingMessage.value
        if (currentEdit != null) {
            viewModelScope.launch {
                chatRepo.editMessage(chatId, currentEdit.id, text)
                    .onSuccess { _editingMessage.value = null }
                    .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not edit message")) }
            }
            return
        }
        val activeChat = chat.value ?: return
        val reply = _replyingTo.value
        viewModelScope.launch {
            chatRepo.sendMessage(
                chat = activeChat,
                senderName = senderName,
                type = "text",
                textRaw = text,
                replyTo = reply
            ).onSuccess {
                _replyingTo.value = null
                typingStopJob?.cancel()
            }.onFailure {
                _events.tryEmit(it.toFriendlyMessage("Could not send message"))
            }
        }
    }

    fun sendMediaMessage(
        senderName: String,
        type: String,
        caption: String,
        mediaData: String,
        fileName: String? = null,
        fileMimeType: String? = null,
        durationMs: Long? = null
    ) {
        val activeChat = chat.value ?: return
        val reply = _replyingTo.value
        viewModelScope.launch {
            chatRepo.sendMessage(
                chat = activeChat,
                senderName = senderName,
                type = type,
                textRaw = caption,
                mediaData = mediaData,
                fileName = fileName,
                fileMimeType = fileMimeType,
                durationMs = durationMs,
                replyTo = reply
            ).onSuccess {
                _replyingTo.value = null
            }.onFailure {
                _events.tryEmit(it.toFriendlyMessage("Could not send attachment"))
            }
        }
    }

    fun deleteMessageForAll(messageId: String) {
        viewModelScope.launch {
            chatRepo.deleteMessageForAll(chatId, messageId)
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not delete message")) }
        }
    }

    fun markVisibleMessagesRead(messages: List<Message>, readReceiptsEnabled: Boolean) {
        viewModelScope.launch {
            chatRepo.markMessagesDeliveredAndRead(chatId, messages, readReceiptsEnabled)
        }
    }

    fun updateGroupInfo(title: String, description: String, avatarData: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            chatRepo.updateGroupInfo(chatId, title, description, avatarData)
                .onSuccess {
                    _events.tryEmit("Group updated")
                    onDone()
                }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not update group")) }
        }
    }

    fun addMember(uid: String) {
        viewModelScope.launch {
            chatRepo.addGroupMember(chatId, uid)
                .onSuccess { _events.tryEmit("Member added") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not add member")) }
        }
    }

    fun removeMember(uid: String) {
        viewModelScope.launch {
            chatRepo.removeGroupMember(chatId, uid)
                .onSuccess { _events.tryEmit("Member removed") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not remove member")) }
        }
    }

    fun toggleAdmin(uid: String, makeAdmin: Boolean) {
        viewModelScope.launch {
            chatRepo.toggleGroupAdmin(chatId, uid, makeAdmin)
                .onSuccess { _events.tryEmit(if (makeAdmin) "Promoted to admin" else "Admin removed") }
                .onFailure { _events.tryEmit(it.toFriendlyMessage("Could not update admin")) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        typingStopJob?.cancel()
    }

    companion object {
        fun factory(chatId: String, currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ChatViewModel(chatId, currentUserId, ChatRepository(db))
            }
        }
    }
}
