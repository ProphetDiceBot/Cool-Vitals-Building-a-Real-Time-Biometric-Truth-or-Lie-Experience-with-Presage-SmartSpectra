package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun logStatement_validPayload_createsAndObservesStatement() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = StatementRepository(firestore)

        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logStatement(
                transcript = "I swear I was at the library all evening.",
                isLie = true,
                deceptionScore = 82,
                verdict = "LIE",
                pulseRate = 104,
                breathingRate = 23,
                hrvRmssd = 19.5,
                expression = "Fear 88%",
                reason = "Pulse spike (104 bpm) • Suppressed HRV (19.5 ms)",
            ).getOrThrow()
        }
        assertTrue(logId.isNotEmpty())

        val statements = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStatements(aliceUid).first { list -> list.any { it.id == logId } }
        }
        val created = statements.first { it.id == logId }
        assertEquals("LIE", created.verdict)
        assertTrue(created.isLie)
        assertEquals(82L, created.deceptionScore)
    }

    @Test
    fun getStatementById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = StatementRepository(firestore)
        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.logStatement(
                transcript = "I have never met that person before.",
                isLie = true,
                deceptionScore = 74,
                verdict = "LIE",
                pulseRate = 98,
                breathingRate = 20,
                hrvRmssd = 22.0,
                expression = "Surprise 75%",
                reason = "Elevated pulse (98 bpm)",
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = StatementRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getStatementById(logId = logId, targetUserId = aliceUid)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeStatements_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = StatementRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeStatements(aliceUid).first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
