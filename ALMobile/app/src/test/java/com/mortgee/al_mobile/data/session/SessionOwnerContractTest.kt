package com.mortgee.al_mobile.data.session

import com.mortgee.al_mobile.data.model.BackendRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SessionOwnerContractTest {

    private class FakeSessionOwner(
        private val storage: CompletedSessionStorage,
    ) : SessionOwner {

        private val _sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private var currentGeneration = 0L
        private var activeAttempt: SessionAttempt? = null

        override suspend fun startSignInAttempt(): SessionAttempt? {
            if (_sessionState.value !is SessionState.SignedOut) return null
            val attempt = SessionAttempt(UUID.randomUUID().toString(), SessionAttempt.Type.SIGN_IN)
            activeAttempt = attempt
            _sessionState.value = SessionState.Establishing(attempt.attemptId)
            return attempt
        }

        override suspend fun startRestoreAttempt(): SessionAttempt? {
            if (_sessionState.value !is SessionState.SignedOut) return null
            val attempt = SessionAttempt(UUID.randomUUID().toString(), SessionAttempt.Type.RESTORE)
            activeAttempt = attempt
            _sessionState.value = SessionState.Restoring(attempt.attemptId)
            return attempt
        }

        override suspend fun commitSession(
            attempt: SessionAttempt,
            uid: String,
            email: String,
            role: BackendRole,
            displayName: String?,
            firstName: String?,
            lastName: String?,
            phoneNumber: String?,
            createdAt: String?,
            updatedAt: String?
        ): Boolean {
            if (activeAttempt != attempt) return false

            val saveSuccess = storage.saveCompletedUid(uid)
            if (!saveSuccess) {
                _sessionState.value = SessionState.SignedOut
                activeAttempt = null
                return false
            }

            currentGeneration++
            val sessionKey = SessionKey(uid, currentGeneration)
            _sessionState.value = SessionState.Authenticated(
                key = sessionKey,
                uid = uid,
                email = email,
                role = role,
                displayName = displayName,
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            activeAttempt = null
            return true
        }

        override suspend fun invalidateSession(): Boolean {
            activeAttempt = null
            _sessionState.value = SessionState.SignedOut
            return storage.clearCompletedUid()
        }

        override suspend fun signOut(): Boolean {
            return invalidateSession()
        }

        override suspend fun isAttemptCurrent(attempt: SessionAttempt): Boolean {
            return activeAttempt == attempt
        }

        override suspend fun cancelAttempt(attempt: SessionAttempt) {
            if (activeAttempt == attempt) {
                activeAttempt = null
                _sessionState.value = SessionState.SignedOut
            }
        }
    }

    private class FakeAuthAdapter : com.mortgee.al_mobile.data.auth.AuthAdapter {
        override val currentUid: String? = null
        var signOutCount = 0

        override suspend fun signIn(email: String, password: String) =
            com.mortgee.al_mobile.data.auth.AuthResult.Failure(com.mortgee.al_mobile.data.model.SignInResult.GenericFailure)

        override suspend fun createAccount(email: String, password: String) =
            com.mortgee.al_mobile.data.auth.AuthResult.Failure(com.mortgee.al_mobile.data.model.SignInResult.GenericFailure)

        override suspend fun getToken(uid: String, forceRefresh: Boolean) = Result.success("token")

        override suspend fun signOut(): Boolean {
            signOutCount++
            return true
        }
    }

    private class MemoryStorage : CompletedSessionStorage {
        private var storedUid: String? = null
        var shouldFailSave = false
        var shouldFailClear = false

        override suspend fun saveCompletedUid(uid: String): Boolean {
            if (shouldFailSave) return false
            storedUid = uid
            return true
        }

        override suspend fun getCompletedUid(): String? = storedUid

        override suspend fun clearCompletedUid(): Boolean {
            if (shouldFailClear) return false
            storedUid = null
            return true
        }
    }

    @Test
    fun `startSignInAttempt acquires attempt and updates state to Establishing`() = runTest {
        val storage = MemoryStorage()
        val owner = FakeSessionOwner(storage)

        val attempt = owner.startSignInAttempt()
        assertNotNull(attempt)
        assertEquals(SessionAttempt.Type.SIGN_IN, attempt?.type)

        val currentState = owner.sessionState.value
        assertTrue(currentState is SessionState.Establishing)
        assertEquals(attempt?.attemptId, (currentState as SessionState.Establishing).attemptId)
    }

    @Test
    fun `cannot start new attempt while another attempt is in progress`() = runTest {
        val storage = MemoryStorage()
        val owner = FakeSessionOwner(storage)

        val attempt1 = owner.startSignInAttempt()
        assertNotNull(attempt1)

        val attempt2 = owner.startSignInAttempt()
        assertNull(attempt2)
    }

    @Test
    fun `commitSession publishes Authenticated state with fresh generation and saves UID marker`() = runTest {
        val storage = MemoryStorage()
        val owner = FakeSessionOwner(storage)

        val attempt = owner.startSignInAttempt()!!
        val committed = owner.commitSession(
            attempt = attempt,
            uid = "user_abc",
            email = "broker@example.com",
            role = BackendRole.BROKER,
            displayName = "Bob Broker"
        )

        assertTrue(committed)
        assertEquals("user_abc", storage.getCompletedUid())

        val state = owner.sessionState.value
        assertTrue(state is SessionState.Authenticated)
        val authState = state as SessionState.Authenticated
        assertEquals("user_abc", authState.uid)
        assertEquals("broker@example.com", authState.email)
        assertEquals(BackendRole.BROKER, authState.role)
        assertEquals("Bob Broker", authState.displayName)
        assertEquals(1L, authState.key.generation)
        assertEquals(authState.key, owner.currentKey)
    }

    @Test
    fun `commitSession rejects stale attempt`() = runTest {
        val storage = MemoryStorage()
        val owner = FakeSessionOwner(storage)

        val staleAttempt = SessionAttempt("stale_id", SessionAttempt.Type.SIGN_IN)
        val committed = owner.commitSession(
            attempt = staleAttempt,
            uid = "user_xyz",
            email = "underwriter@example.com",
            role = BackendRole.UNDERWRITER
        )

        assertFalse(committed)
        assertFalse(owner.sessionState.value.isAuthenticated)
        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `commitSession fails closed if storage write fails`() = runTest {
        val storage = MemoryStorage().apply { shouldFailSave = true }
        val owner = FakeSessionOwner(storage)

        val attempt = owner.startSignInAttempt()!!
        val committed = owner.commitSession(
            attempt = attempt,
            uid = "user_abc",
            email = "test@example.com",
            role = BackendRole.BORROWER
        )

        assertFalse(committed)
        assertTrue(owner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `invalidateSession transitions to SignedOut and clears marker`() = runTest {
        val storage = MemoryStorage()
        val owner = FakeSessionOwner(storage)

        val attempt = owner.startSignInAttempt()!!
        owner.commitSession(attempt, "user_123", "a@b.com", BackendRole.BORROWER)

        assertTrue(owner.sessionState.value.isAuthenticated)

        val invalidated = owner.invalidateSession()
        assertTrue(invalidated)
        assertTrue(owner.sessionState.value is SessionState.SignedOut)
        assertNull(storage.getCompletedUid())
        assertNull(owner.currentKey)
    }

    @Test
    fun `cancelAttempt resets state to SignedOut if attempt matches`() = runTest {
        val storage = MemoryStorage()
        val owner = FakeSessionOwner(storage)

        val attempt = owner.startSignInAttempt()!!
        assertTrue(owner.sessionState.value is SessionState.Establishing)

        owner.cancelAttempt(attempt)
        assertTrue(owner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `DefaultSessionOwner isAttemptCurrent returns true only while attempt is active`() = runTest {
        val storage = MemoryStorage()
        val authAdapter = FakeAuthAdapter()
        val owner = DefaultSessionOwner(storage, authAdapter)

        val attempt = owner.startSignInAttempt()!!
        assertTrue(owner.isAttemptCurrent(attempt))

        val otherAttempt = SessionAttempt("other_id", SessionAttempt.Type.SIGN_IN)
        assertFalse(owner.isAttemptCurrent(otherAttempt))

        owner.cancelAttempt(attempt)
        assertFalse(owner.isAttemptCurrent(attempt))
    }

    @Test
    fun `DefaultSessionOwner invalidated attempt cannot publish session or save marker`() = runTest {
        val storage = MemoryStorage()
        val authAdapter = FakeAuthAdapter()
        val owner = DefaultSessionOwner(storage, authAdapter)

        val attempt = owner.startSignInAttempt()!!
        assertTrue(owner.isAttemptCurrent(attempt))

        // Invalidate while attempt is in progress
        owner.invalidateSession()
        assertFalse(owner.isAttemptCurrent(attempt))

        val committed = owner.commitSession(
            attempt = attempt,
            uid = "user_invalidated",
            email = "stale@example.com",
            role = BackendRole.BORROWER,
            displayName = "Stale Borrower"
        )

        assertFalse(committed)
        assertTrue(owner.sessionState.value is SessionState.SignedOut)
        assertNull(storage.getCompletedUid())
        assertNull(owner.currentKey)
    }

    @Test
    fun `DefaultSessionOwner cancelled attempt cannot publish session or save marker`() = runTest {
        val storage = MemoryStorage()
        val authAdapter = FakeAuthAdapter()
        val owner = DefaultSessionOwner(storage, authAdapter)

        val attempt = owner.startSignInAttempt()!!
        owner.cancelAttempt(attempt)
        assertFalse(owner.isAttemptCurrent(attempt))

        val committed = owner.commitSession(
            attempt = attempt,
            uid = "user_cancelled",
            email = "cancelled@example.com",
            role = BackendRole.BORROWER,
            displayName = "Cancelled Borrower"
        )

        assertFalse(committed)
        assertTrue(owner.sessionState.value is SessionState.SignedOut)
        assertNull(storage.getCompletedUid())
        assertNull(owner.currentKey)
    }

    @Test
    fun `DefaultSessionOwner superseded attempt cannot publish or overwrite newer attempt or session`() = runTest {
        val storage = MemoryStorage()
        val authAdapter = FakeAuthAdapter()
        val owner = DefaultSessionOwner(storage, authAdapter)

        val attempt1 = owner.startSignInAttempt()!!
        owner.cancelAttempt(attempt1)

        val attempt2 = owner.startSignInAttempt()!!
        assertFalse(owner.isAttemptCurrent(attempt1))
        assertTrue(owner.isAttemptCurrent(attempt2))

        // Attempt 1 tries to commit late while attempt 2 is active
        val staleCommit = owner.commitSession(
            attempt = attempt1,
            uid = "stale_uid",
            email = "stale@example.com",
            role = BackendRole.BORROWER
        )
        assertFalse(staleCommit)
        assertNull(storage.getCompletedUid())
        assertTrue(owner.sessionState.value is SessionState.Establishing)
        assertTrue(owner.isAttemptCurrent(attempt2))

        // Attempt 2 commits normally
        val validCommit = owner.commitSession(
            attempt = attempt2,
            uid = "fresh_uid",
            email = "fresh@example.com",
            role = BackendRole.BORROWER
        )
        assertTrue(validCommit)
        assertEquals("fresh_uid", storage.getCompletedUid())
        assertFalse(owner.isAttemptCurrent(attempt2))
    }
}
