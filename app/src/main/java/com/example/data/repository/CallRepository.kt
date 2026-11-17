package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.IceCandidateModel
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

class CallRepository(
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
            ?: throw IllegalStateException("User must be signed in before calling.")
    }

    fun observeMyCalls(uid: String): Flow<List<CallSession>> = flow {
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
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

    fun observeCall(callId: String, uid: String): Flow<CallSession?> = flow {
        val path = "calls/$callId"
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeIceCandidates(callId: String, uid: String): Flow<List<IceCandidateModel>> = flow {
        val path = "calls/$callId/iceCandidates"
        emitAll(
            db.collection("calls").document(callId).collection("iceCandidates")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(IceCandidateModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun createCall(
        callerName: String,
        callerAvatar: String?,
        calleeId: String,
        calleeName: String,
        calleeAvatar: String?,
        callType: String
    ): Result<String> {
        val docRef = db.collection("calls").document()
        return try {
            val uid = requireUserId()
            require(calleeId.isNotBlank() && calleeId != uid)
            require(callType in listOf("audio", "video"))
            val payload = buildMap<String, Any> {
                put("callerId", uid)
                put("callerName", callerName.trim().take(50).ifBlank { "Caller" })
                if (!callerAvatar.isNullOrBlank()) put("callerAvatar", callerAvatar.take(350_000))
                put("calleeId", calleeId)
                put("calleeName", calleeName.trim().take(50).ifBlank { "User" })
                if (!calleeAvatar.isNullOrBlank()) put("calleeAvatar", calleeAvatar.take(350_000))
                put("callType", callType)
                put("status", "ringing")
                put("participantUids", listOf(uid, calleeId))
                put("durationSeconds", 0L)
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

    suspend fun setOfferSdp(callId: String, sdp: String, type: String): Result<Unit> {
        val docRef = db.collection("calls").document(callId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "offerSdp" to sdp.take(25_000),
                    "offerType" to type.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String, sdp: String, type: String): Result<Unit> {
        val docRef = db.collection("calls").document(callId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "status" to "answered",
                    "answerSdp" to sdp.take(25_000),
                    "answerType" to type.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Long = 0L): Result<Unit> {
        val docRef = db.collection("calls").document(callId)
        return try {
            requireUserId()
            require(status in listOf("answered", "rejected", "ended", "missed"))
            docRef.update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86_400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Terminal state lock or already ended is safe to ignore on hangup
            Result.failure(e)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String
    ): Result<Unit> {
        val docRef = db.collection("calls").document(callId)
            .collection("iceCandidates").document()
        return try {
            val uid = requireUserId()
            val payload = mapOf(
                "senderId" to uid,
                "participantUids" to participantUids,
                "sdpMid" to sdpMid.take(64),
                "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
                "sdp" to sdp.take(4096),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
