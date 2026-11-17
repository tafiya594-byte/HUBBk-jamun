package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.StatusUpdate
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class StatusRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing statuses.")
    }

    fun observeActiveStatuses(uid: String): Flow<List<StatusUpdate>> {
        val ownFlow = flow {
            emitAll(
                db.collection("statuses")
                    .whereEqualTo("userId", uid)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .catch { error ->
                        val fe = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fe == null && error is CancellationException) throw error
                        if (auth.currentUser?.uid == uid) {
                            handleFirestoreError(fe ?: error, OperationType.LIST, "statuses")
                        }
                        throw error
                    }
            )
        }

        val feedFlow = flow {
            emitAll(
                db.collection("statuses")
                    .whereIn("privacy", listOf("everyone", "contacts"))
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .catch { error ->
                        val fe = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fe == null && error is CancellationException) throw error
                        if (auth.currentUser?.uid == uid) {
                            handleFirestoreError(fe ?: error, OperationType.LIST, "statuses")
                        }
                        throw error
                    }
            )
        }

        return combine(ownFlow, feedFlow) { own, feed ->
            val now = Date()
            (own + feed)
                .distinctBy { it.id }
                .filter { status ->
                    val exp = status.expiresAt?.toDate()
                    exp != null && exp.after(now)
                }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    suspend fun postStatus(
        authorName: String,
        authorAvatar: String?,
        type: String,
        textRaw: String,
        backgroundHex: String,
        mediaData: String?,
        privacy: String
    ): Result<Unit> {
        val docRef = db.collection("statuses").document()
        return try {
            val uid = requireUserId()
            require(type in listOf("text", "image", "video"))
            require(privacy in listOf("everyone", "contacts"))
            val text = textRaw.trim().take(500)
            if (type == "text") {
                require(text.isNotBlank()) { "Status text cannot be empty." }
            } else {
                require(!mediaData.isNullOrBlank()) { "Media attachment is required." }
            }

            val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("authorName", authorName.trim().take(50).ifBlank { "User" })
                if (!authorAvatar.isNullOrBlank()) put("authorAvatar", authorAvatar.take(350_000))
                put("type", type)
                put("text", text)
                put("backgroundHex", backgroundHex.take(16).ifBlank { "#008955" })
                if (!mediaData.isNullOrBlank()) put("mediaData", mediaData.take(750_000))
                put("privacy", privacy)
                put("viewerUids", emptyList<String>())
                put("expiresAt", expiresAt)
                put("createdAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markStatusViewed(statusId: String): Result<Unit> {
        val docRef = db.collection("statuses").document(statusId)
        return try {
            val uid = requireUserId()
            docRef.update("viewerUids", FieldValue.arrayUnion(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        val docRef = db.collection("statuses").document(statusId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
