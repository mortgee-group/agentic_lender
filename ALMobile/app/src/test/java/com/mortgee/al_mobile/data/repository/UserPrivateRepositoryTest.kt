package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.network.PrivateTokenException
import com.mortgee.al_mobile.data.network.PrivateTokenSeam
import com.mortgee.al_mobile.data.session.SessionAttempt
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.data.session.UserPrivateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class UserPrivateRepositoryTest {

    private lateinit var fakeSessionOwner: FakeSessionOwner
    private lateinit var fakeUserPrivateStore: FakeUserPrivateStore
    private lateinit var fakePrivateTokenSeam: FakePrivateTokenSeam
    private lateinit var repository: DefaultUserPrivateRepository

    @Before
    fun setUp() {
        fakeSessionOwner = FakeSessionOwner()
        fakeUserPrivateStore = FakeUserPrivateStore(fakeSessionOwner)
        fakePrivateTokenSeam = FakePrivateTokenSeam(fakeSessionOwner)
        repository = DefaultUserPrivateRepository(
            sessionOwner = fakeSessionOwner,
            userPrivateStore = fakeUserPrivateStore,
            privateTokenSeam = fakePrivateTokenSeam
        )
    }

    @Test
    fun `getPrivatePreference returns NoActiveSession when no session exists`() = runTest {
        fakeSessionOwner.setSignedOut()

        val result = repository.getPrivatePreference(key = "theme")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PrivateTokenException.NoActiveSession)
    }

    @Test
    fun `getPrivatePreference carries original key and returns value when session remains current`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)

        // Pre-populate store for user_A
        fakeUserPrivateStore.putString(keyA, "theme", "dark")

        val result = repository.getPrivatePreference(entryKey = keyA, key = "theme")

        assertTrue(result.isSuccess)
        assertEquals("dark", result.getOrNull())
    }

    @Test
    fun `getPrivatePreference returns StaleSession on user transition A to B`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)
        fakeUserPrivateStore.putString(keyA, "setting", "valueA")

        // Caller captures keyA, but before repository finishes session changes to B
        val keyB = SessionKey("user_B", 2L)
        fakeSessionOwner.setAuthenticated(keyB, "userB@example.com", BackendRole.BORROWER)

        val result = repository.getPrivatePreference(entryKey = keyA, key = "setting")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PrivateTokenException.StaleSession)
        val staleEx = ex as PrivateTokenException.StaleSession
        assertEquals(keyA, staleEx.capturedKey)
        assertEquals(keyB, staleEx.currentKey)
    }

    @Test
    fun `getPrivatePreference returns StaleSession on user re-authentication A to A with fresh generation`() = runTest {
        val keyA1 = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA1, "userA@example.com", BackendRole.BORROWER)
        fakeUserPrivateStore.putString(keyA1, "profile_pic", "pic1.png")

        // User A re-authenticates -> generation advances from 1 to 2
        val keyA2 = SessionKey("user_A", 2L)
        fakeSessionOwner.setAuthenticated(keyA2, "userA@example.com", BackendRole.BORROWER)

        val result = repository.getPrivatePreference(entryKey = keyA1, key = "profile_pic")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PrivateTokenException.StaleSession)
        val staleEx = ex as PrivateTokenException.StaleSession
        assertEquals(keyA1, staleEx.capturedKey)
        assertEquals(keyA2, staleEx.currentKey)
    }

    @Test
    fun `setPrivatePreference carries original key and writes value when session remains current`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)

        val result = repository.setPrivatePreference(entryKey = keyA, key = "notifications", value = "enabled")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        assertEquals("enabled", fakeUserPrivateStore.getString("user_A", "notifications"))
    }

    @Test
    fun `setPrivatePreference returns StaleSession on user transition A to B`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)

        // Session transitions to user B
        val keyB = SessionKey("user_B", 2L)
        fakeSessionOwner.setAuthenticated(keyB, "userB@example.com", BackendRole.BORROWER)

        val result = repository.setPrivatePreference(entryKey = keyA, key = "draft", value = "secret_A")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PrivateTokenException.StaleSession)
        val staleEx = ex as PrivateTokenException.StaleSession
        assertEquals(keyA, staleEx.capturedKey)
        assertEquals(keyB, staleEx.currentKey)
        // User B store must not contain user A's draft
        assertFalse(fakeUserPrivateStore.containsKey("user_B", "draft"))
    }

    @Test
    fun `setPrivatePreference returns StaleSession on user re-authentication A to A with fresh generation`() = runTest {
        val keyA1 = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA1, "userA@example.com", BackendRole.BORROWER)

        // Re-authentication produces generation 2
        val keyA2 = SessionKey("user_A", 2L)
        fakeSessionOwner.setAuthenticated(keyA2, "userA@example.com", BackendRole.BORROWER)

        val result = repository.setPrivatePreference(entryKey = keyA1, key = "note", value = "old_gen_note")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PrivateTokenException.StaleSession)
        val staleEx = ex as PrivateTokenException.StaleSession
        assertEquals(keyA1, staleEx.capturedKey)
        assertEquals(keyA2, staleEx.currentKey)
    }

    @Test
    fun `fetchPrivateRemoteData carries original key and returns remote result when session is current`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)

        val result = repository.fetchPrivateRemoteData(entryKey = keyA) { token ->
            "response_data_for_$token"
        }

        assertTrue(result.isSuccess)
        assertEquals("response_data_for_token_for_user_A", result.getOrNull())
    }

    @Test
    fun `fetchPrivateRemoteData returns StaleSession on user transition A to B during execution`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)

        val result = repository.fetchPrivateRemoteData(entryKey = keyA) { token ->
            // Switch session to user B during request execution
            val keyB = SessionKey("user_B", 2L)
            fakeSessionOwner.setAuthenticated(keyB, "userB@example.com", BackendRole.BORROWER)
            "response_data"
        }

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PrivateTokenException.StaleSession)
        val staleEx = ex as PrivateTokenException.StaleSession
        assertEquals(keyA, staleEx.capturedKey)
        assertEquals(SessionKey("user_B", 2L), staleEx.currentKey)
    }

    @Test
    fun `fetchPrivateRemoteData returns StaleSession on user re-authentication A to A with fresh generation`() = runTest {
        val keyA1 = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA1, "userA@example.com", BackendRole.BORROWER)

        val result = repository.fetchPrivateRemoteData(entryKey = keyA1) { token ->
            // Re-authenticate user A to generation 2 during execution
            val keyA2 = SessionKey("user_A", 2L)
            fakeSessionOwner.setAuthenticated(keyA2, "userA@example.com", BackendRole.BORROWER)
            "response_data"
        }

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PrivateTokenException.StaleSession)
        val staleEx = ex as PrivateTokenException.StaleSession
        assertEquals(keyA1, staleEx.capturedKey)
        assertEquals(SessionKey("user_A", 2L), staleEx.currentKey)
    }

    @Test
    fun `fetchPrivateRemoteData propagates network error when session key remains valid`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)

        val networkError = IOException("Server unavailable")
        val result = repository.fetchPrivateRemoteData<String>(entryKey = keyA) {
            throw networkError
        }

        assertTrue(result.isFailure)
        assertEquals(networkError, result.exceptionOrNull())
    }

    @Test
    fun `suspend functions rethrow CancellationException`() = runTest {
        val keyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(keyA, "userA@example.com", BackendRole.BORROWER)
        fakeUserPrivateStore.shouldThrowCancellation = true

        try {
            repository.getPrivatePreference(entryKey = keyA, key = "test")
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // Success
        }

        try {
            repository.setPrivatePreference(entryKey = keyA, key = "test", value = "val")
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // Success
        }
    }

    // --- Helper Fakes for Unit Tests ---

    private class FakeSessionOwner : SessionOwner {
        private val _sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        override val currentKey: SessionKey?
            get() = sessionState.value.sessionKey

        fun setAuthenticated(key: SessionKey, email: String, role: BackendRole) {
            _sessionState.value = SessionState.Authenticated(
                key = key,
                uid = key.uid,
                email = email,
                role = role
            )
        }

        fun setSignedOut() {
            _sessionState.value = SessionState.SignedOut
        }

        override suspend fun startSignInAttempt(): SessionAttempt? = null
        override suspend fun startRestoreAttempt(): SessionAttempt? = null
        override suspend fun commitSession(
            attempt: SessionAttempt,
            uid: String,
            email: String,
            role: BackendRole,
            displayName: String?
        ): Boolean = false

        override suspend fun invalidateSession(): Boolean {
            setSignedOut()
            return true
        }

        override suspend fun signOut(): Boolean {
            setSignedOut()
            return true
        }

        override suspend fun cancelAttempt(attempt: SessionAttempt) {}
    }

    private class FakeUserPrivateStore(
        private val sessionOwner: SessionOwner
    ) : UserPrivateStore {
        var shouldThrowCancellation = false
        private val storage = mutableMapOf<String, String>()

        override suspend fun getString(uid: String, key: String): String? {
            if (shouldThrowCancellation) throw CancellationException("Cancelled")
            return storage["${uid}_$key"]
        }

        override suspend fun putString(sessionKey: SessionKey, key: String, value: String): Boolean {
            if (shouldThrowCancellation) throw CancellationException("Cancelled")
            val current = sessionOwner.currentKey
            if (current != sessionKey) return false
            storage["${sessionKey.uid}_$key"] = value
            return true
        }

        override suspend fun removeString(sessionKey: SessionKey, key: String): Boolean {
            val current = sessionOwner.currentKey
            if (current != sessionKey) return false
            storage.remove("${sessionKey.uid}_$key")
            return true
        }

        override suspend fun clearUserPartition(sessionKey: SessionKey): Boolean {
            val current = sessionOwner.currentKey
            if (current != sessionKey) return false
            val prefix = "${sessionKey.uid}_"
            storage.keys.filter { it.startsWith(prefix) }.forEach { storage.remove(it) }
            return true
        }

        override suspend fun containsKey(uid: String, key: String): Boolean {
            return storage.containsKey("${uid}_$key")
        }
    }

    private class FakePrivateTokenSeam(
        private val sessionOwner: SessionOwner
    ) : PrivateTokenSeam {

        override suspend fun getToken(entryKey: SessionKey?, forceRefresh: Boolean): Result<String> {
            val key = entryKey ?: sessionOwner.currentKey
                ?: return Result.failure(PrivateTokenException.NoActiveSession())
            val current = sessionOwner.currentKey
            if (current != key) {
                return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = current))
            }
            return Result.success("token_for_${key.uid}")
        }

        override suspend fun <T> executePrivate(
            entryKey: SessionKey?,
            isMutation: Boolean,
            block: suspend (token: String) -> T
        ): Result<T> {
            val key = entryKey ?: sessionOwner.currentKey
                ?: return Result.failure(PrivateTokenException.NoActiveSession())

            val currentBefore = sessionOwner.currentKey
            if (currentBefore != key) {
                return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = currentBefore))
            }

            val token = "token_for_${key.uid}"
            val result = try {
                block(token)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return Result.failure(e)
            }

            val currentAfter = sessionOwner.currentKey
            if (currentAfter != key) {
                return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = currentAfter))
            }

            return Result.success(result)
        }

        override fun isAllowedOrigin(url: HttpUrl): Boolean = true
        override fun isAllowedOrigin(urlString: String): Boolean = true
    }
}
