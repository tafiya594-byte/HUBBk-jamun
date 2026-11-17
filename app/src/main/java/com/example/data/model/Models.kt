package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserPublic(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val about: String = "",
    val avatarData: String? = null,
    val online: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    val lastSeenPrivacy: String = "everyone",
    val statusPrivacy: String = "everyone",
    val readReceipts: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPrivate(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val themeMode: String = "system",
    val blockedUids: List<String> = emptyList(),
    val pinnedChatIds: List<String> = emptyList(),
    val archivedChatIds: List<String> = emptyList(),
    val mutedChatIds: List<String> = emptyList(),
    val fcmToken: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chat(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val title: String = "",
    val description: String = "",
    val groupAvatarData: String? = null,
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val createdBy: String = "",
    val lastMessageText: String = "",
    val lastMessageType: String = "none",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val typingUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "text",
    val text: String = "",
    val mediaData: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val durationMs: Long? = null,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val forwardedFrom: String? = null,
    val edited: Boolean = false,
    val deletedForAll: Boolean = false,
    val deliveredTo: List<String> = emptyList(),
    val readBy: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val type: String = "text",
    val text: String = "",
    val backgroundHex: String = "#008955",
    val mediaData: String? = null,
    val privacy: String = "everyone",
    val viewerUids: List<String> = emptyList(),
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatar: String? = null,
    val calleeId: String = "",
    val calleeName: String = "",
    val calleeAvatar: String? = null,
    val callType: String = "audio",
    val status: String = "ringing",
    val participantUids: List<String> = emptyList(),
    val offerSdp: String? = null,
    val offerType: String? = null,
    val answerSdp: String? = null,
    val answerType: String? = null,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IceCandidateModel(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val sdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val chatId: String? = null,
    val reason: String = "",
    val details: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null
)
