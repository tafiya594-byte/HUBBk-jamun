package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.UserPublic
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing chats.")
    }

    fun directChatId(uidA: String, uidB: String): String {
        return if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
    }

    fun observeMyChats(uid: String): Flow<List<Chat>> = flow {
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChat(chatId: String, uid: String): Flow<Chat?> = flow {
        val path = "chats/$chatId"
        emitAll(
            db.collection("chats").document(chatId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<Message>> = flow {
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getOrCreateDirectChat(otherUser: UserPublic): Result<String> {
        var path = "chats"
        return try {
            val uid = requireUserId()
            require(otherUser.uid.isNotBlank() && otherUser.uid != uid) {
                "Invalid recipient user."
            }
            val chatId = directChatId(uid, otherUser.uid)
            val docRef = db.collection("chats").document(chatId)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val members = listOf(uid, otherUser.uid).sorted()
                val payload = mapOf(
                    "type" to "direct",
                    "title" to "",
                    "description" to "",
                    "memberUids" to members,
                    "adminUids" to listOf(uid),
                    "createdBy" to uid,
                    "lastMessageText" to "",
                    "lastMessageType" to "none",
                    "lastMessageSenderId" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "typingUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(
        titleRaw: String,
        descriptionRaw: String,
        memberUidsRaw: List<String>,
        avatarData: String?
    ): Result<String> {
        val docRef = db.collection("chats").document()
        return try {
            val uid = requireUserId()
            val title = titleRaw.trim()
            val description = descriptionRaw.trim().take(250)
            require(title.length in 1..80) { "Group name must be 1-80 characters." }
            val members = (memberUidsRaw + uid).distinct().take(50)
            require(members.size >= 2) { "Select at least one member for the group." }

            val payload = buildMap<String, Any> {
                put("type", "group")
                put("title", title)
                put("description", description)
                if (!avatarData.isNullOrBlank()) put("groupAvatarData", avatarData.take(350_000))
                put("memberUids", members)
                put("adminUids", listOf(uid))
                put("createdBy", uid)
                put("lastMessageText", "Group \"$title\" created")
                put("lastMessageType", "text")
                put("lastMessageSenderId", uid)
                put("lastMessageAt", FieldValue.serverTimestamp())
                put("typingUids", emptyList<String>())
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        chat: Chat,
        senderName: String,
        type: String,
        textRaw: String,
        mediaData: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        durationMs: Long? = null,
        replyTo: Message? = null,
        forwardedFrom: String? = null
    ): Result<Unit> {
        val chatRef = db.collection("chats").document(chat.id)
        val msgRef = chatRef.collection("messages").document()
        return try {
            val uid = requireUserId()
            require(chat.id.isNotBlank())
            require(type in listOf("text", "image", "video", "voice", "document"))
            val cleanText = textRaw.trim().take(4000)
            if (type == "text") {
                require(cleanText.isNotBlank()) { "Message cannot be empty." }
            }
            val safeMembers = if (uid in chat.memberUids) chat.memberUids else (chat.memberUids + uid)

            val payload = buildMap<String, Any> {
                put("chatId", chat.id)
                put("senderId", uid)
                put("senderName", senderName.trim().take(50).ifBlank { "User" })
                put("type", type)
                put("text", cleanText)
                if (!mediaData.isNullOrBlank()) put("mediaData", mediaData.take(750_000))
                if (!fileName.isNullOrBlank()) put("fileName", fileName.take(160))
                if (!fileMimeType.isNullOrBlank()) put("fileMimeType", fileMimeType.take(100))
                if (durationMs != null) put("durationMs", durationMs.coerceIn(0L, 600_000L))
                if (replyTo != null) {
                    put("replyToMessageId", replyTo.id.take(128))
                    val preview = when (replyTo.type) {
                        "image" -> "Photo"
                        "video" -> "Video"
                        "voice" -> "Voice message"
                        "document" -> replyTo.fileName ?: "Document"
                        else -> replyTo.text
                    }.take(300)
                    put("replyToText", preview)
                    put("replyToSenderName", replyTo.senderName.take(50))
                }
                if (!forwardedFrom.isNullOrBlank()) put("forwardedFrom", forwardedFrom.take(50))
                put("edited", false)
                put("deletedForAll", false)
                put("deliveredTo", listOf(uid))
                put("readBy", listOf(uid))
                put("memberUids", safeMembers.take(50))
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val lastPreview = when (type) {
                "image" -> if (cleanText.isNotBlank()) "Photo: $cleanText" else "Photo"
                "video" -> if (cleanText.isNotBlank()) "Video: $cleanText" else "Video"
                "voice" -> "Voice message"
                "document" -> "Document: ${fileName ?: "File"}"
                else -> cleanText
            }.take(300)

            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessageText" to lastPreview,
                    "lastMessageType" to type,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "typingUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, newTextRaw: String): Result<Unit> {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            requireUserId()
            val newText = newTextRaw.trim().take(4000)
            require(newText.isNotBlank()) { "Edited message cannot be empty." }
            msgRef.update(
                mapOf(
                    "text" to newText,
                    "edited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForAll(chatId: String, messageId: String): Result<Unit> {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            requireUserId()
            msgRef.update(
                mapOf(
                    "text" to "This message was deleted",
                    "deletedForAll" to true,
                    "mediaData" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesDeliveredAndRead(
        chatId: String,
        messages: List<Message>,
        myReadReceiptsEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val pending = messages.filter { msg ->
            msg.senderId != uid &&
                (uid !in msg.deliveredTo || (myReadReceiptsEnabled && uid !in msg.readBy))
        }
        if (pending.isEmpty()) return Result.success(Unit)

        return try {
            val batch = db.batch()
            pending.take(25).forEach { msg ->
                val ref = db.collection("chats").document(chatId)
                    .collection("messages").document(msg.id)
                val updates = buildMap<String, Any> {
                    if (uid !in msg.deliveredTo) {
                        put("deliveredTo", FieldValue.arrayUnion(uid))
                    }
                    if (myReadReceiptsEnabled && uid !in msg.readBy) {
                        put("readBy", FieldValue.arrayUnion(uid))
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.update(ref, updates)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages")
            Result.failure(e)
        }
    }

    suspend fun setTyping(chatId: String, isTyping: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val chatRef = db.collection("chats").document(chatId)
        return try {
            val op = if (isTyping) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
            chatRef.update(
                mapOf(
                    "typingUids" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroupInfo(
        chatId: String,
        titleRaw: String,
        descriptionRaw: String,
        avatarData: String?
    ): Result<Unit> {
        val chatRef = db.collection("chats").document(chatId)
        return try {
            requireUserId()
            val title = titleRaw.trim()
            require(title.length in 1..80) { "Group name must be 1-80 characters." }
            val updates = buildMap<String, Any> {
                put("title", title)
                put("description", descriptionRaw.trim().take(250))
                if (avatarData != null) put("groupAvatarData", avatarData.take(350_000))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            chatRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            Result.failure(e)
        }
    }

    suspend fun addGroupMember(chatId: String, newMemberUid: String): Result<Unit> {
        val chatRef = db.collection("chats").document(chatId)
        return try {
            requireUserId()
            chatRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(newMemberUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(chatId: String, targetUid: String): Result<Unit> {
        val chatRef = db.collection("chats").document(chatId)
        return try {
            requireUserId()
            chatRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(targetUid),
                    "adminUids" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleGroupAdmin(chatId: String, targetUid: String, makeAdmin: Boolean): Result<Unit> {
        val chatRef = db.collection("chats").document(chatId)
        return try {
            requireUserId()
            val op = if (makeAdmin) FieldValue.arrayUnion(targetUid) else FieldValue.arrayRemove(targetUid)
            chatRef.update(
                mapOf(
                    "adminUids" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            Result.failure(e)
        }
    }
}
