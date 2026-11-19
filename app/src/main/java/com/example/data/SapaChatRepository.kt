package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SapaChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storage: FirebaseStorage = Firebase.storage
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")

    private fun isValidFirestoreId(id: String): Boolean =
        id.length in 1..128 && id.matches(Regex("^[a-zA-Z0-9_\\-]+$"))

    // --- USER PROFILE & DIRECTORY ---

    suspend fun ensureUserInitialized(
        firebaseUser: FirebaseUser,
        idAuthRequest: PendingIdAuthRequest? = null
    ): Result<IdAuthInitOutcome> {
        val uid = firebaseUser.uid
        val privatePath = "users/$uid"
        val publicPath = "public_profiles/$uid"
        return try {
            require(isValidFirestoreId(uid)) { "Invalid UID format." }
            val requestedId = idAuthRequest?.pathnerId
                ?.let { PathnerIdHelper.normalize(it) }
                ?.takeIf { PathnerIdHelper.isValid(it) }

            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("public_profiles").document(uid)
            val privateSnap = privateRef.get().await()
            val publicSnap = publicRef.get().await()

            val existingPathnerId = privateSnap.getString("pathnerId")
                ?: publicSnap.getString("pathnerId")

            var resolvedPathnerId = existingPathnerId
            var newlyCreatedId: String? = null
            var notice: String? = null

            if (requestedId != null) {
                val handleRef = db.collection("pathner_ids").document(requestedId)
                val handleSnap = handleRef.get().await()
                val currentOwnerUid = handleSnap.getString("userId")

                when (idAuthRequest.mode) {
                    IdAuthMode.REGISTER -> {
                        when {
                            !handleSnap.exists() -> {
                                handleRef.set(
                                    mapOf(
                                        "pathnerId" to requestedId,
                                        "userId" to uid,
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                                if (!existingPathnerId.isNullOrBlank() && existingPathnerId != requestedId) {
                                    runCatching {
                                        db.collection("pathner_ids").document(existingPathnerId).delete().await()
                                    }
                                }
                                resolvedPathnerId = requestedId
                                newlyCreatedId = requestedId
                                notice = "ID User @$requestedId berhasil dibuat!"
                            }
                            currentOwnerUid == uid -> {
                                resolvedPathnerId = requestedId
                                newlyCreatedId = requestedId
                                notice = "ID User @$requestedId sudah aktif untuk akun Anda."
                            }
                            else -> {
                                return Result.success(
                                    IdAuthInitOutcome.Rejected(
                                        reason = "ID @$requestedId sudah digunakan pengguna lain. Silakan buat ID baru yang berbeda.",
                                        suggestRegister = true
                                    )
                                )
                            }
                        }
                    }

                    IdAuthMode.LOGIN -> {
                        when {
                            currentOwnerUid == uid || existingPathnerId == requestedId -> {
                                // Ensure handle doc exists if migrated from older profile
                                if (!handleSnap.exists()) {
                                    handleRef.set(
                                        mapOf(
                                            "pathnerId" to requestedId,
                                            "userId" to uid,
                                            "createdAt" to FieldValue.serverTimestamp(),
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    ).await()
                                }
                                resolvedPathnerId = requestedId
                                notice = "Selamat datang kembali, @$requestedId!"
                            }
                            !handleSnap.exists() -> {
                                val hint = if (!existingPathnerId.isNullOrBlank()) {
                                    " ID terdaftar Anda adalah @$existingPathnerId."
                                } else {
                                    " Silakan buat ID terlebih dahulu di tab Daftar."
                                }
                                return Result.success(
                                    IdAuthInitOutcome.Rejected(
                                        reason = "ID @$requestedId belum terdaftar.$hint",
                                        suggestRegister = existingPathnerId.isNullOrBlank()
                                    )
                                )
                            }
                            else -> {
                                val hint = if (!existingPathnerId.isNullOrBlank()) {
                                    " ID akun Google Anda adalah @$existingPathnerId."
                                } else {
                                    " Gunakan akun Google yang terhubung dengan ID tersebut atau buat ID baru di tab Daftar."
                                }
                                return Result.success(
                                    IdAuthInitOutcome.Rejected(
                                        reason = "ID @$requestedId milik akun lain.$hint",
                                        suggestRegister = false
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Direct Google sign-in without an ID yet: auto-create a friendly User ID once
            if (resolvedPathnerId.isNullOrBlank()) {
                val candidate = PathnerIdHelper.generateRandomId()
                val candidateRef = db.collection("pathner_ids").document(candidate)
                val claimed = runCatching {
                    if (!candidateRef.get().await().exists()) {
                        candidateRef.set(
                            mapOf(
                                "pathnerId" to candidate,
                                "userId" to uid,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        candidate
                    } else null
                }.getOrNull()
                if (claimed != null) {
                    resolvedPathnerId = claimed
                    newlyCreatedId = claimed
                }
            }

            val defaultDisplayName = (
                resolvedPathnerId?.takeIf { idAuthRequest?.mode == IdAuthMode.REGISTER }
                    ?: firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: resolvedPathnerId
                    ?: firebaseUser.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Pengguna Partner Bank"
                ).take(80)
            val statusText = "Ada di Partner Bank"
            val photoUrl = firebaseUser.photoUrl?.toString()?.take(500)
            val email = firebaseUser.email?.take(200)

            if (!privateSnap.exists()) {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "pathnerId" to resolvedPathnerId,
                    "email" to email,
                    "displayName" to defaultDisplayName,
                    "photoUrl" to photoUrl,
                    "statusText" to statusText,
                    "discoverable" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privateRef.set(payload).await()
            } else if (!resolvedPathnerId.isNullOrBlank() && resolvedPathnerId != existingPathnerId) {
                privateRef.update(
                    mapOf(
                        "pathnerId" to resolvedPathnerId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!publicSnap.exists()) {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "pathnerId" to resolvedPathnerId,
                    "displayName" to defaultDisplayName,
                    "photoUrl" to photoUrl,
                    "statusText" to statusText,
                    "discoverable" to true,
                    "online" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                publicRef.set(payload).await()
            } else {
                val publicUpdates = buildMap<String, Any> {
                    put("online", true)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (!resolvedPathnerId.isNullOrBlank() && resolvedPathnerId != existingPathnerId) {
                        put("pathnerId", resolvedPathnerId)
                    }
                }
                publicRef.update(publicUpdates).await()
            }
            Result.success(
                IdAuthInitOutcome.Authenticated(
                    pathnerId = resolvedPathnerId,
                    newlyCreatedId = newlyCreatedId,
                    notice = notice
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$privatePath | $publicPath")
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "public_profiles/$uid"
        return try {
            val docRef = db.collection("public_profiles").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "online" to online,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observePrivateUser(userId: String): Flow<PrivateUser?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyProfile(userId: String): Flow<PublicProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "public_profiles/$userId"
        emitAll(
            db.collection("public_profiles").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDiscoverableContacts(currentUserId: String): Flow<List<PublicProfile>> = flow {
        require(currentUserId.isNotBlank()) { "User ID must not be blank" }
        val path = "public_profiles"
        emitAll(
            db.collection(path)
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.userId != currentUserId }
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun findUserById(targetId: String): Result<PublicProfile?> {
        val rawTrimmed = targetId.trim()
        val normalizedHandle = PathnerIdHelper.normalize(rawTrimmed)
        val path = "pathner_ids/$normalizedHandle | public_profiles/$rawTrimmed"
        return try {
            requireUserId()
            // 1. Try resolving custom Pathner ID handle first (e.g. "budi_01" or "@budi_01")
            if (PathnerIdHelper.isValid(normalizedHandle)) {
                val handleSnap = db.collection("pathner_ids").document(normalizedHandle).get().await()
                val mappedUid = handleSnap.getString("userId")
                if (!mappedUid.isNullOrBlank() && isValidFirestoreId(mappedUid)) {
                    return try {
                        val profileSnap = db.collection("public_profiles").document(mappedUid).get().await()
                        val profile = if (profileSnap.exists()) {
                            profileSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        } else null
                        Result.success(profile)
                    } catch (e: FirebaseFirestoreException) {
                        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Result.success(null)
                        } else {
                            throw e
                        }
                    }
                }
            }
            // 2. Fall back to direct Firebase UID lookup
            val cleanUid = rawTrimmed.removePrefix("@")
            require(isValidFirestoreId(cleanUid)) { "Format ID Partner Bank tidak valid." }
            val snap = try {
                db.collection("public_profiles").document(cleanUid).get().await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    return Result.success(null)
                }
                throw e
            }
            val profile = if (snap.exists()) {
                snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun updatePathnerId(newRawId: String): Result<String> {
        val normalized = PathnerIdHelper.normalize(newRawId)
        val path = "pathner_ids/$normalized"
        return try {
            val activeUid = requireUserId()
            val validationErr = PathnerIdHelper.validationError(normalized)
            require(validationErr == null) { validationErr ?: "Format ID tidak valid." }

            val handleRef = db.collection("pathner_ids").document(normalized)
            val existingHandle = handleRef.get().await()
            val ownerUid = existingHandle.getString("userId")
            if (existingHandle.exists() && ownerUid != activeUid) {
                return Result.failure(IllegalStateException("ID @$normalized sudah dipakai pengguna lain."))
            }

            val privateRef = db.collection("users").document(activeUid)
            val oldPathnerId = privateRef.get().await().getString("pathnerId")

            if (!existingHandle.exists()) {
                handleRef.set(
                    mapOf(
                        "pathnerId" to normalized,
                        "userId" to activeUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val updates = mapOf(
                "pathnerId" to normalized,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            privateRef.update(updates).await()
            db.collection("public_profiles").document(activeUid).update(updates).await()

            if (!oldPathnerId.isNullOrBlank() && oldPathnerId != normalized) {
                runCatching {
                    db.collection("pathner_ids").document(oldPathnerId).delete().await()
                }
            }
            Result.success(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        statusText: String,
        discoverable: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "public_profiles/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            val cleanName = displayName.trim()
            val cleanStatus = statusText.trim()
            require(cleanName.length in 1..80) { "Nama tampilan harus antara 1 sampai 80 karakter." }
            require(cleanStatus.length <= 160) { "Status maksimal 160 karakter." }

            val updates = mapOf(
                "displayName" to cleanName,
                "statusText" to cleanStatus,
                "discoverable" to discoverable,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(activeUid).update(updates).await()
            db.collection("public_profiles").document(activeUid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePhoto(photoData: String?): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "public_profiles/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            if (photoData != null) {
                require(photoData.length <= 120_000) { "Ukuran foto profil terlalu besar." }
            }
            val photoValue: Any = photoData ?: FieldValue.delete()
            val updates = mapOf(
                "photoData" to photoValue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(activeUid).update(updates).await()
            db.collection("public_profiles").document(activeUid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun hideChats(chatIds: Collection<String>, currentHidden: List<String>): Result<Unit> {
        val cleanIds = chatIds.filter { isValidFirestoreId(it) }.distinct()
        if (cleanIds.isEmpty()) return Result.success(Unit)
        return updateUserHiddenField(
            fieldName = "hiddenChatIds",
            newList = (currentHidden + cleanIds).distinct().takeLast(500)
        )
    }

    suspend fun hideCalls(callIds: Collection<String>, currentHidden: List<String>): Result<Unit> {
        val cleanIds = callIds.filter { isValidFirestoreId(it) }.distinct()
        if (cleanIds.isEmpty()) return Result.success(Unit)
        return updateUserHiddenField(
            fieldName = "hiddenCallIds",
            newList = (currentHidden + cleanIds).distinct().takeLast(500)
        )
    }

    suspend fun hideContacts(contactUids: Collection<String>, currentHidden: List<String>): Result<Unit> {
        val cleanUids = contactUids.filter { isValidFirestoreId(it) }.distinct()
        if (cleanUids.isEmpty()) return Result.success(Unit)
        return updateUserHiddenField(
            fieldName = "hiddenContactUids",
            newList = (currentHidden + cleanUids).distinct().takeLast(500)
        )
    }

    suspend fun restoreHiddenContacts(): Result<Unit> {
        return updateUserHiddenField(
            fieldName = "hiddenContactUids",
            newList = emptyList()
        )
    }

    private suspend fun updateUserHiddenField(
        fieldName: String,
        newList: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            db.collection("users").document(activeUid).update(
                mapOf(
                    fieldName to newList,
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

    private suspend fun unhideUserItemIfPresent(fieldName: String, targetId: String) {
        val activeUid = auth.currentUser?.uid ?: return
        runCatching {
            val userRef = db.collection("users").document(activeUid)
            val snap = userRef.get().await()
            val currentList = (snap.get(fieldName) as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty()
            if (targetId in currentList) {
                userRef.update(
                    mapOf(
                        fieldName to currentList.filterNot { it == targetId },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    // --- CHATS ---

    fun observeChats(currentUserId: String): Flow<List<ChatRoom>> = flow {
        require(currentUserId.isNotBlank()) { "User ID must not be blank" }
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun openOrCreateSelfChat(myProfile: PublicProfile): Result<ChatRoom> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val chatId = "self_$uid"
        val path = "chats/$chatId"
        return try {
            val activeUid = requireUserId()
            val targetChatId = "self_$activeUid"
            val docRef = db.collection("chats").document(targetChatId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                unhideUserItemIfPresent("hiddenChatIds", targetChatId)
                val existing = snap.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val names = mapOf(activeUid to myProfile.displayName.ifBlank { "Anda" })
            val photos = buildMap {
                myProfile.photoUrl?.takeIf { it.isNotBlank() }?.let { put(activeUid, it) }
            }
            val payload = mapOf<String, Any?>(
                "title" to "Catatan Pribadi (Anda)",
                "groupChat" to false,
                "createdBy" to activeUid,
                "participantUids" to listOf(activeUid),
                "participantNames" to names,
                "participantPhotos" to photos,
                "lastMessageText" to "Simpan pesan, foto, video, dan file di sini",
                "lastMessageType" to MessageType.TEXT.name,
                "lastMessageSenderId" to activeUid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatRoom(
                    id = targetChatId,
                    title = "Catatan Pribadi (Anda)",
                    groupChat = false,
                    createdBy = activeUid,
                    participantUids = listOf(activeUid),
                    participantNames = names,
                    participantPhotos = photos
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun openOrCreateDirectChat(
        myProfile: PublicProfile,
        otherProfile: PublicProfile
    ): Result<ChatRoom> {
        if (myProfile.userId == otherProfile.userId) {
            return openOrCreateSelfChat(myProfile)
        }
        val chatId = if (myProfile.userId < otherProfile.userId) {
            "${myProfile.userId}_${otherProfile.userId}"
        } else {
            "${otherProfile.userId}_${myProfile.userId}"
        }
        val path = "chats/$chatId"
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId)) { "Invalid chat ID." }
            val docRef = db.collection("chats").document(chatId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                unhideUserItemIfPresent("hiddenChatIds", chatId)
                unhideUserItemIfPresent("hiddenContactUids", otherProfile.userId)
                val existing = snap.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val participants = listOf(activeUid, otherProfile.userId).distinct().sorted()
            val names = mapOf(
                activeUid to myProfile.displayName.ifBlank { "Anda" },
                otherProfile.userId to otherProfile.displayName.ifBlank { "Pengguna" }
            )
            val photos = buildMap {
                myProfile.photoUrl?.takeIf { it.isNotBlank() }?.let { put(activeUid, it) }
                otherProfile.photoUrl?.takeIf { it.isNotBlank() }?.let { put(otherProfile.userId, it) }
            }
            val payload = mapOf<String, Any?>(
                "groupChat" to false,
                "createdBy" to activeUid,
                "participantUids" to participants,
                "participantNames" to names,
                "participantPhotos" to photos,
                "lastMessageText" to "Obrolan dimulai",
                "lastMessageType" to MessageType.TEXT.name,
                "lastMessageSenderId" to activeUid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatRoom(
                    id = chatId,
                    groupChat = false,
                    createdBy = activeUid,
                    participantUids = participants,
                    participantNames = names,
                    participantPhotos = photos
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(
        groupTitle: String,
        myProfile: PublicProfile,
        members: List<PublicProfile>
    ): Result<ChatRoom> {
        val docRef = db.collection("chats").document()
        val path = docRef.path
        return try {
            val activeUid = requireUserId()
            val cleanTitle = groupTitle.trim()
            require(cleanTitle.length in 1..100) { "Nama grup harus antara 1 sampai 100 karakter." }
            val allProfiles = (listOf(myProfile) + members).distinctBy { it.userId }.take(20)
            val participantUids = allProfiles.map { it.userId }
            val names = allProfiles.associate { it.userId to it.displayName.ifBlank { "Anggota" } }
            val photos = buildMap {
                allProfiles.forEach { p ->
                    p.photoUrl?.takeIf { it.isNotBlank() }?.let { put(p.userId, it) }
                }
            }
            val payload = mapOf<String, Any?>(
                "title" to cleanTitle,
                "groupChat" to true,
                "createdBy" to activeUid,
                "participantUids" to participantUids,
                "participantNames" to names,
                "participantPhotos" to photos,
                "lastMessageText" to "${myProfile.displayName} membuat grup \"$cleanTitle\"",
                "lastMessageType" to MessageType.TEXT.name,
                "lastMessageSenderId" to activeUid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: ChatRoom(
                    id = docRef.id,
                    title = cleanTitle,
                    groupChat = true,
                    createdBy = activeUid,
                    participantUids = participantUids,
                    participantNames = names,
                    participantPhotos = photos
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(chatId: String, isTyping: Boolean): Result<Unit> {
        val activeUid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (!isValidFirestoreId(chatId)) return Result.success(Unit)
        val path = "chats/$chatId"
        return try {
            val value: Any = if (isTyping) FieldValue.serverTimestamp() else FieldValue.delete()
            db.collection("chats").document(chatId).update(
                mapOf(
                    "typingUsers.$activeUid" to value,
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

    // --- MESSAGES ---

    fun observeMessages(chatId: String, currentUserId: String): Flow<List<ChatMessage>> = flow {
        require(chatId.isNotBlank() && currentUserId.isNotBlank())
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.copy(pendingSend = doc.metadata.hasPendingWrites())
                        }
                        .filterNot { currentUserId in it.hiddenForUids }
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendTextMessage(
        chat: ChatRoom,
        senderName: String,
        text: String,
        replyTo: ChatMessage? = null
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Pesan tidak boleh kosong."))
        return sendMessageInternal(
            chat = chat,
            senderName = senderName,
            messageType = MessageType.TEXT,
            text = cleanText.take(2000),
            attachment = null,
            replyTo = replyTo,
            previewText = cleanText.take(200)
        )
    }

    suspend fun uploadChatImageToStorage(
        chatId: String,
        fileName: String,
        mimeType: String,
        jpegBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId)) { "ID obrolan tidak valid." }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(80)
            val objectPath = "chats/$chatId/images/${activeUid}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}_$safeName"
            val storageRef = storage.reference.child(objectPath)
            val metadata = storageMetadata {
                contentType = mimeType
                setCustomMetadata("chatId", chatId)
                setCustomMetadata("senderId", activeUid)
            }
            val uploadTask = storageRef.putBytes(jpegBytes, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    onProgress((snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(1f)
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChatAudioToStorage(
        chatId: String,
        fileName: String,
        mimeType: String,
        audioBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId)) { "ID obrolan tidak valid." }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(80)
            val objectPath = "chats/$chatId/audio/${activeUid}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}_$safeName"
            val storageRef = storage.reference.child(objectPath)
            val metadata = storageMetadata {
                contentType = mimeType
                setCustomMetadata("chatId", chatId)
                setCustomMetadata("senderId", activeUid)
            }
            val uploadTask = storageRef.putBytes(audioBytes, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    onProgress((snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(1f)
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAttachmentMessage(
        chat: ChatRoom,
        senderName: String,
        caption: String,
        attachment: PreparedAttachment,
        replyTo: ChatMessage? = null
    ): Result<Unit> {
        val cleanCaption = caption.trim().take(2000)
        val preview = when (attachment.messageType) {
            MessageType.IMAGE -> if (cleanCaption.isNotBlank()) "📷 $cleanCaption" else "📷 Foto"
            MessageType.VIDEO -> if (cleanCaption.isNotBlank()) "🎥 $cleanCaption" else "🎥 Video (${MediaHelper.formatDurationMs(attachment.durationMs)})"
            MessageType.AUDIO -> "🎤 Pesan suara (${MediaHelper.formatDurationMs(attachment.durationMs)})"
            MessageType.FILE -> "📎 ${attachment.fileName}"
            else -> cleanCaption.ifBlank { "Lampiran" }
        }
        return sendMessageInternal(
            chat = chat,
            senderName = senderName,
            messageType = attachment.messageType,
            text = cleanCaption,
            attachment = attachment,
            replyTo = replyTo,
            previewText = preview.take(200)
        )
    }

    suspend fun sendCallLogMessage(
        chat: ChatRoom,
        senderName: String,
        callType: CallType,
        summaryText: String
    ): Result<Unit> {
        return sendMessageInternal(
            chat = chat,
            senderName = senderName,
            messageType = MessageType.CALL,
            text = summaryText.take(200),
            attachment = null,
            replyTo = null,
            previewText = summaryText.take(200)
        )
    }

    suspend fun forwardMessage(
        sourceMessage: ChatMessage,
        targetChat: ChatRoom,
        senderName: String
    ): Result<Unit> {
        if (sourceMessage.deleted) {
            return Result.failure(IllegalArgumentException("Pesan yang sudah dihapus tidak dapat diteruskan."))
        }
        val type = sourceMessage.parsedType
        val cleanText = sourceMessage.text.trim().take(2000)
        val attachment = if (type != MessageType.TEXT && type != MessageType.CALL) {
            PreparedAttachment(
                messageType = type,
                fileName = sourceMessage.fileName ?: when (type) {
                    MessageType.IMAGE -> "foto.jpg"
                    MessageType.VIDEO -> "video.mp4"
                    MessageType.AUDIO -> "pesan_suara.m4a"
                    MessageType.FILE -> "dokumen"
                    else -> "lampiran"
                },
                mimeType = sourceMessage.mimeType ?: when (type) {
                    MessageType.IMAGE -> "image/jpeg"
                    MessageType.VIDEO -> "video/mp4"
                    MessageType.AUDIO -> "audio/mp4"
                    else -> "application/octet-stream"
                },
                fileSizeBytes = sourceMessage.fileSizeBytes ?: 0L,
                durationMs = sourceMessage.durationMs,
                mediaUrl = sourceMessage.mediaUrl,
                mediaData = sourceMessage.mediaData,
                thumbnailData = sourceMessage.thumbnailData,
                localFilePath = sourceMessage.localFilePath
            )
        } else {
            null
        }
        val preview = sourceMessage.previewSummary().ifBlank { "Pesan diteruskan" }.take(200)
        return sendMessageInternal(
            chat = targetChat,
            senderName = senderName,
            messageType = type,
            text = cleanText,
            attachment = attachment,
            replyTo = null,
            previewText = preview,
            forwarded = true
        )
    }

    private suspend fun sendMessageInternal(
        chat: ChatRoom,
        senderName: String,
        messageType: MessageType,
        text: String,
        attachment: PreparedAttachment?,
        replyTo: ChatMessage?,
        previewText: String,
        forwarded: Boolean = false
    ): Result<Unit> {
        val chatId = chat.id
        var docPath = "chats/$chatId/messages"
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId)) { "ID obrolan tidak valid." }
            val safeSenderName = senderName.trim().ifBlank { "Pengguna" }.take(80)
            val msgRef = db.collection("chats").document(chatId).collection("messages").document()
            docPath = msgRef.path

            val replyPreview = replyTo?.let {
                when (it.parsedType) {
                    MessageType.IMAGE -> it.text.ifBlank { "📷 Foto" }
                    MessageType.VIDEO -> it.text.ifBlank { "🎥 Video" }
                    MessageType.AUDIO -> "🎤 Pesan suara (${MediaHelper.formatDurationMs(it.durationMs)})"
                    MessageType.FILE -> "📎 ${it.fileName ?: "Dokumen"}"
                    else -> it.text
                }.take(180)
            }

            val payload = mapOf<String, Any?>(
                "chatId" to chatId,
                "senderId" to activeUid,
                "senderName" to safeSenderName,
                "participantUids" to chat.participantUids,
                "messageType" to messageType.name,
                "text" to text,
                "fileName" to attachment?.fileName?.take(255),
                "mimeType" to attachment?.mimeType?.take(128),
                "fileSizeBytes" to attachment?.fileSizeBytes?.coerceIn(0L, 500_000_000L),
                "durationMs" to attachment?.durationMs?.coerceIn(0L, 86_400_000L),
                "mediaUrl" to attachment?.mediaUrl?.take(1000),
                "mediaData" to attachment?.mediaData?.take(450_000),
                "thumbnailData" to attachment?.thumbnailData?.take(180_000),
                "localFilePath" to attachment?.localFilePath?.take(500),
                "replyToMessageId" to replyTo?.id?.takeIf { it.isNotBlank() }?.take(128),
                "replyToSenderName" to replyTo?.senderName?.takeIf { it.isNotBlank() }?.take(80),
                "replyToPreview" to replyPreview?.takeIf { it.isNotBlank() },
                "sentAt" to FieldValue.serverTimestamp(),
                "deliveredToUids" to listOf(activeUid),
                "readByUids" to listOf(activeUid),
                "forwarded" to if (forwarded) true else null,
                "deleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            msgRef.set(payload).await()

            val isSelf = chat.isSelfChat(activeUid)
            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastMessageText" to previewText.take(500),
                    "lastMessageType" to messageType.name,
                    "lastMessageSenderId" to activeUid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageDeliveredAt" to if (isSelf) FieldValue.serverTimestamp() else FieldValue.delete(),
                    "lastMessageReadAt" to if (isSelf) FieldValue.serverTimestamp() else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markChatMessagesDelivered(chatId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (!isValidFirestoreId(chatId)) return Result.success(Unit)
        val path = "chats/$chatId/messages"
        return try {
            val snap = db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
            val undelivered = snap.documents
                .mapNotNull { it.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .filter { msg ->
                    msg.id.isNotBlank() &&
                        msg.senderId != uid &&
                        (uid !in msg.deliveredToUids || msg.deliveredAt == null) &&
                        uid !in msg.readByUids
                }
            if (undelivered.isEmpty()) return Result.success(Unit)

            val messagesRef = db.collection("chats").document(chatId).collection("messages")
            undelivered.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msg ->
                    val ref = messagesRef.document(msg.id)
                    val updatedDelivered = (msg.deliveredToUids + uid).distinct().take(20)
                    val updates = mutableMapOf<String, Any>(
                        "deliveredToUids" to updatedDelivered,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (msg.deliveredAt == null) {
                        updates["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                    batch.update(ref, updates)
                }
                batch.commit().await()
            }

            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastMessageDeliveredAt" to FieldValue.serverTimestamp(),
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

    suspend fun markMessagesRead(chatId: String, messages: List<ChatMessage>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val unread = messages.filter {
            it.senderId != uid &&
                it.id.isNotBlank() &&
                (uid !in it.readByUids || uid !in it.deliveredToUids || it.readAt == null)
        }
        if (unread.isEmpty()) return Result.success(Unit)
        val path = "chats/$chatId/messages"
        return try {
            val messagesRef = db.collection("chats").document(chatId).collection("messages")
            unread.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msg ->
                    val ref = messagesRef.document(msg.id)
                    val updatedDelivered = (msg.deliveredToUids + uid).distinct().take(20)
                    val updatedReaders = (msg.readByUids + uid).distinct().take(20)
                    val updates = mutableMapOf<String, Any>(
                        "deliveredToUids" to updatedDelivered,
                        "readByUids" to updatedReaders,
                        "readAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (msg.deliveredAt == null) {
                        updates["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                    batch.update(ref, updates)
                }
                batch.commit().await()
            }

            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastMessageDeliveredAt" to FieldValue.serverTimestamp(),
                    "lastMessageReadAt" to FieldValue.serverTimestamp(),
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

    suspend fun softDeleteMessage(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            require(isValidFirestoreId(chatId) && isValidFirestoreId(messageId))
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "deleted" to true,
                        "text" to "",
                        "mediaUrl" to FieldValue.delete(),
                        "mediaData" to FieldValue.delete(),
                        "thumbnailData" to FieldValue.delete(),
                        "fileName" to FieldValue.delete(),
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

    suspend fun deleteMessageForMe(chatId: String, message: ChatMessage): Result<Unit> {
        val path = "chats/$chatId/messages/${message.id}"
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId) && isValidFirestoreId(message.id))
            val updatedHidden = (message.hiddenForUids + activeUid).distinct().take(20)
            db.collection("chats").document(chatId)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "hiddenForUids" to updatedHidden,
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

    suspend fun deleteMessagesForMe(chatId: String, messages: List<ChatMessage>): Result<Int> {
        val path = "chats/$chatId/messages"
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId))
            val targets = messages
                .filter { it.id.isNotBlank() && isValidFirestoreId(it.id) && activeUid !in it.hiddenForUids }
                .distinctBy { it.id }
            if (targets.isEmpty()) return Result.success(0)

            val messagesRef = db.collection("chats").document(chatId).collection("messages")
            targets.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msg ->
                    val updatedHidden = (msg.hiddenForUids + activeUid).distinct().take(20)
                    batch.update(
                        messagesRef.document(msg.id),
                        mapOf(
                            "hiddenForUids" to updatedHidden,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(targets.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun softDeleteMessagesForEveryone(chatId: String, messages: List<ChatMessage>): Result<Int> {
        val path = "chats/$chatId/messages"
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(chatId))
            val targets = messages
                .filter {
                    it.id.isNotBlank() &&
                        isValidFirestoreId(it.id) &&
                        it.senderId == activeUid &&
                        !it.deleted
                }
                .distinctBy { it.id }
            if (targets.isEmpty()) return Result.success(0)

            val messagesRef = db.collection("chats").document(chatId).collection("messages")
            targets.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msg ->
                    batch.update(
                        messagesRef.document(msg.id),
                        mapOf(
                            "deleted" to true,
                            "text" to "",
                            "mediaUrl" to FieldValue.delete(),
                            "mediaData" to FieldValue.delete(),
                            "thumbnailData" to FieldValue.delete(),
                            "fileName" to FieldValue.delete(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(targets.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- VIDEO & VOICE CALL SIGNALING ---

    fun observeCalls(currentUserId: String): Flow<List<CallSession>> = flow {
        require(currentUserId.isNotBlank())
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun startCall(
        chat: ChatRoom,
        myProfile: PublicProfile,
        callType: CallType
    ): Result<CallSession> {
        val callRef = db.collection("calls").document()
        val path = callRef.path
        return try {
            val activeUid = requireUserId()
            val calleeUid = chat.otherParticipantUid(activeUid)
            val calleeName = chat.resolvedTitle(activeUid).take(80)
            val calleePhoto = chat.resolvedPhotoUrl(activeUid)?.take(500)
            val participants = listOf(activeUid, calleeUid).distinct().sorted()

            val payload = mapOf<String, Any?>(
                "chatId" to chat.id,
                "callerId" to activeUid,
                "callerName" to myProfile.displayName.ifBlank { "Anda" }.take(80),
                "callerPhotoUrl" to myProfile.photoUrl?.take(500),
                "calleeId" to calleeUid,
                "calleeName" to calleeName,
                "calleePhotoUrl" to calleePhoto,
                "participantUids" to participants,
                "callType" to callType.name,
                "status" to CallStatus.RINGING.name,
                "callerCameraOn" to (callType == CallType.VIDEO),
                "callerMuted" to false,
                "calleeCameraOn" to (callType == CallType.VIDEO),
                "calleeMuted" to false,
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            callRef.set(payload).await()
            val created = callRef.get().await()
                .toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: CallSession(
                    id = callRef.id,
                    chatId = chat.id,
                    callerId = activeUid,
                    callerName = myProfile.displayName,
                    calleeId = calleeUid,
                    calleeName = calleeName,
                    participantUids = participants,
                    callType = callType.name,
                    status = CallStatus.RINGING.name,
                    callerCameraOn = callType == CallType.VIDEO
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptCall(callId: String, videoEnabled: Boolean): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            require(isValidFirestoreId(callId))
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to CallStatus.ACTIVE.name,
                    "calleeCameraOn" to videoEnabled,
                    "calleeMuted" to false,
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

    suspend fun updateCallMediaState(
        call: CallSession,
        cameraOn: Boolean,
        muted: Boolean
    ): Result<Unit> {
        val path = "calls/${call.id}"
        return try {
            val activeUid = requireUserId()
            require(isValidFirestoreId(call.id))
            val isCaller = call.callerId == activeUid
            val updates = if (isCaller) {
                mapOf(
                    "callerCameraOn" to cameraOn,
                    "callerMuted" to muted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "calleeCameraOn" to cameraOn,
                    "calleeMuted" to muted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("calls").document(call.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun finishCall(
        callId: String,
        finalStatus: CallStatus,
        durationSeconds: Long
    ): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            require(isValidFirestoreId(callId))
            require(finalStatus in setOf(CallStatus.ENDED, CallStatus.DECLINED, CallStatus.MISSED))
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to finalStatus.name,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86_400L),
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
}
