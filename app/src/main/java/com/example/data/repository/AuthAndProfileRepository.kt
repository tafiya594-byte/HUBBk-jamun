package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.UserPrivate
import com.example.data.model.UserPublic
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class AuthAndProfileRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google first.")
    }

    suspend fun attemptSilentSignIn(context: Context): Result<FirebaseUser?> {
        auth.currentUser?.let { return Result.success(it) }
        return try {
            val clientId = context.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(context, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                Result.success(authResult.user)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(null)
        }
    }

    suspend fun signInWithGoogleInteractive(activity: Activity): Result<FirebaseUser> {
        return try {
            val clientId = activity.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val response = credentialManager.getCredential(activity, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Google Sign-In returned empty user."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type received."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                val uid = auth.currentUser?.uid
                if (!uid.isNullOrBlank()) {
                    try {
                        db.collection("users_public").document(uid).update(
                            mapOf(
                                "online" to false,
                                "lastSeen" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } catch (_: Exception) {
                    }
                }
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    fun observeMyPublicProfile(uid: String): Flow<UserPublic?> = flow {
        val path = "users_public/$uid"
        emitAll(
            db.collection("users_public").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserPublic::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeMyPrivateProfile(uid: String): Flow<UserPrivate?> = flow {
        val path = "users_private/$uid"
        emitAll(
            db.collection("users_private").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeAllPublicUsers(currentUid: String): Flow<List<UserPublic>> = flow {
        val path = "users_public"
        emitAll(
            db.collection("users_public")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserPublic::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == currentUid) {
                        handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun isUsernameAvailable(usernameRaw: String): Result<Boolean> {
        val clean = usernameRaw.trim().lowercase()
        val path = "usernames/$clean"
        return try {
            val uid = requireUserId()
            if (!clean.matches(Regex("^[a-z0-9_]{3,24}$"))) {
                return Result.success(false)
            }
            val snap = db.collection("usernames").document(clean).get().await()
            val available = !snap.exists() || snap.getString("uid") == uid
            Result.success(available)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createInitialProfile(
        usernameRaw: String,
        displayNameRaw: String,
        aboutRaw: String,
        avatarData: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val email = (auth.currentUser?.email ?: "").take(120)
        val cleanUsername = usernameRaw.trim().lowercase()
        val displayName = displayNameRaw.trim()
        val about = aboutRaw.trim().ifBlank { "Hey there! I am using ABID Messenger." }
        return try {
            require(cleanUsername.matches(Regex("^[a-z0-9_]{3,24}$"))) {
                "Username must be 3-24 lowercase letters, numbers, or underscores."
            }
            require(displayName.length in 1..50) { "Display name must be 1-50 characters." }
            require(about.length <= 160) { "About must be 160 characters or less." }

            val existingUsername = db.collection("usernames").document(cleanUsername).get().await()
            if (existingUsername.exists() && existingUsername.getString("uid") != uid) {
                return Result.failure(IllegalStateException("Username @$cleanUsername is already taken."))
            }

            val batch = db.batch()
            if (!existingUsername.exists()) {
                val usernamePayload = mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(db.collection("usernames").document(cleanUsername), usernamePayload)
            }

            val publicPayload = buildMap<String, Any> {
                put("uid", uid)
                put("username", cleanUsername)
                put("displayName", displayName)
                put("about", about)
                if (!avatarData.isNullOrBlank()) put("avatarData", avatarData.take(350_000))
                put("online", true)
                put("lastSeen", FieldValue.serverTimestamp())
                put("lastSeenPrivacy", "everyone")
                put("statusPrivacy", "everyone")
                put("readReceipts", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(db.collection("users_public").document(uid), publicPayload)

            val privatePayload = mapOf(
                "uid" to uid,
                "email" to email,
                "themeMode" to "system",
                "blockedUids" to emptyList<String>(),
                "pinnedChatIds" to emptyList<String>(),
                "archivedChatIds" to emptyList<String>(),
                "mutedChatIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(db.collection("users_private").document(uid), privatePayload)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users_public/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        currentProfile: UserPublic,
        newUsernameRaw: String,
        displayNameRaw: String,
        aboutRaw: String,
        avatarData: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanNew = newUsernameRaw.trim().lowercase()
        val displayName = displayNameRaw.trim()
        val about = aboutRaw.trim().take(160)
        return try {
            require(cleanNew.matches(Regex("^[a-z0-9_]{3,24}$"))) {
                "Username must be 3-24 lowercase letters, numbers, or underscores."
            }
            require(displayName.length in 1..50) { "Display name must be 1-50 characters." }

            if (cleanNew != currentProfile.username) {
                val check = db.collection("usernames").document(cleanNew).get().await()
                if (check.exists() && check.getString("uid") != uid) {
                    return Result.failure(IllegalStateException("Username @$cleanNew is already taken."))
                }
            }

            val batch = db.batch()
            if (cleanNew != currentProfile.username) {
                batch.set(
                    db.collection("usernames").document(cleanNew),
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                if (currentProfile.username.isNotBlank()) {
                    batch.delete(db.collection("usernames").document(currentProfile.username))
                }
            }

            val updates = buildMap<String, Any> {
                put("username", cleanNew)
                put("displayName", displayName)
                put("about", about)
                if (avatarData != null) put("avatarData", avatarData.take(350_000))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.update(db.collection("users_public").document(uid), updates)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_public/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "users_public/$uid"
        return try {
            db.collection("users_public").document(uid).update(
                mapOf(
                    "online" to online,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Presence update may run before initial profile setup completes; don't crash
            Log.d("Presence", "Skipping presence update: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        lastSeenPrivacy: String,
        statusPrivacy: String,
        readReceipts: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users_public/$uid"
        return try {
            require(lastSeenPrivacy in listOf("everyone", "nobody"))
            require(statusPrivacy in listOf("everyone", "contacts", "nobody"))
            db.collection("users_public").document(uid).update(
                mapOf(
                    "lastSeenPrivacy" to lastSeenPrivacy,
                    "statusPrivacy" to statusPrivacy,
                    "readReceipts" to readReceipts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateThemeMode(themeMode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users_private/$uid"
        return try {
            require(themeMode in listOf("system", "light", "dark"))
            db.collection("users_private").document(uid).update(
                mapOf(
                    "themeMode" to themeMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleChatListFlag(
        field: String,
        chatId: String,
        currentlyInList: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users_private/$uid"
        return try {
            require(field in listOf("pinnedChatIds", "archivedChatIds", "mutedChatIds"))
            val op = if (currentlyInList) FieldValue.arrayRemove(chatId) else FieldValue.arrayUnion(chatId)
            db.collection("users_private").document(uid).update(
                mapOf(
                    field to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBlockUser(targetUid: String, currentlyBlocked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users_private/$uid"
        return try {
            require(targetUid.isNotBlank() && targetUid != uid)
            val op = if (currentlyBlocked) FieldValue.arrayRemove(targetUid) else FieldValue.arrayUnion(targetUid)
            db.collection("users_private").document(uid).update(
                mapOf(
                    "blockedUids" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reportedUserId: String,
        chatId: String?,
        reason: String,
        details: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("reports").document()
        return try {
            val cleanReason = reason.trim().take(100)
            require(cleanReason.isNotBlank()) { "Please select a reason for reporting." }
            val payload = buildMap<String, Any> {
                put("reporterId", uid)
                put("reportedUserId", reportedUserId)
                if (!chatId.isNullOrBlank()) put("chatId", chatId)
                put("reason", cleanReason)
                put("details", details.trim().take(1000))
                put("status", "open")
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

    suspend fun deleteAccount(context: Context, currentUsername: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        return withContext(NonCancellable) {
            try {
                val batch = db.batch()
                if (currentUsername.isNotBlank()) {
                    batch.delete(db.collection("usernames").document(currentUsername))
                }
                batch.delete(db.collection("users_public").document(uid))
                batch.delete(db.collection("users_private").document(uid))
                batch.commit().await()

                try {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {
                }

                user.delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "users_public/$uid")
                Result.failure(e)
            }
        }
    }
}
