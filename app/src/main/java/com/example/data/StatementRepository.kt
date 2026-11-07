package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
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

class StatementRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeStatements(userId: String = requireUserId()): Flow<List<StatementLog>> = flow {
        val path = "users/$userId/statement_logs"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("statement_logs")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StatementLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    suspend fun getStatementById(logId: String, targetUserId: String = requireUserId()): Result<StatementLog?> =
        runCatching {
            val docRef = db.collection("users")
                .document(targetUserId)
                .collection("statement_logs")
                .document(logId)
            try {
                val snapshot = docRef.get().await()
                snapshot.toObject(
                    StatementLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
        }

    suspend fun logStatement(
        transcript: String,
        isLie: Boolean,
        deceptionScore: Int,
        verdict: String,
        pulseRate: Int,
        breathingRate: Int,
        hrvRmssd: Double,
        expression: String,
        reason: String,
        notes: String? = null,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("statement_logs")
            .document()

        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("transcript", transcript.trim().take(1000).ifEmpty { "(Unintelligible speech)" })
            put("isLie", isLie)
            put("deceptionScore", deceptionScore.coerceIn(0, 100).toLong())
            put("verdict", if (verdict == "LIE") "LIE" else "TRUTH")
            put("pulseRate", pulseRate.coerceIn(0, 300).toLong())
            put("breathingRate", breathingRate.coerceIn(0, 120).toLong())
            put("hrvRmssd", hrvRmssd.coerceIn(0.0, 500.0))
            put("expression", expression.trim().take(64).ifEmpty { "Neutral" })
            put("reason", reason.trim().take(500).ifEmpty { "Baseline physiological reading" })
            if (!notes.isNullOrBlank()) {
                put("notes", notes.trim().take(500))
            }
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStatement(logId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(logId.isNotBlank()) { "Statement logId must not be blank" }
        val docRef = db.collection("users")
            .document(uid)
            .collection("statement_logs")
            .document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
