package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.auth.AuthResult
import com.mortgee.al_mobile.data.auth.AuthUser
import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.network.ProfileApiService
import com.mortgee.al_mobile.data.network.model.UserProfileDto
import com.mortgee.al_mobile.data.session.CompletedSessionStorage
import com.mortgee.al_mobile.data.session.DefaultSessionOwner
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.data.session.UserPartitionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiAccountRepositoryTest {

    private lateinit var fakeStorage: FakeCompletedSessionStorage
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var fakeProfileApiService: FakeProfileApiService
    private lateinit var sessionOwner: DefaultSessionOwner
    private lateinit var fakePartitionManager: FakeUserPartitionManager
    private lateinit var repository: ApiAccountRepository

    @Before
    fun setUp() {
        fakeStorage = FakeCompletedSessionStorage()
        fakeAuthAdapter = FakeAuthAdapter()
        fakeProfileApiService = FakeProfileApiService()
        sessionOwner = DefaultSessionOwner(fakeStorage, fakeAuthAdapter)
        fakePartitionManager = FakeUserPartitionManager()

        repository = ApiAccountRepository(
            authAdapter = fakeAuthAdapter,
            profileApiService = fakeProfileApiService,
            sessionOwner = sessionOwner,
            userPartitionManager = fakePartitionManager,
            completedSessionStorage = fakeStorage,
        )
    }

    @Test
    fun `signIn executes all steps in order and commits authenticated session on success`() = runTest {
        val candidateUid = "user-uid-123"
        val candidateEmail = "borrower@example.com"
        val candidateToken = "firebase-id-token-xyz"

        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success(candidateToken))
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = "Test Borrower"
        )

        val result = repository.signIn("borrower@example.com", "password123")

        assertEquals(SignInResult.Success, result)
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)

        val currentState = sessionOwner.sessionState.value
        assertTrue(currentState is SessionState.Authenticated)
        val authState = currentState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(candidateEmail, authState.email)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals("Test Borrower", authState.displayName)
    }

    @Test
    fun `signIn returns Busy when session owner cannot acquire attempt`() = runTest {
        val candidateUid = "user-uid-123"
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "test@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = "test@example.com",
            role = "broker"
        )

        val firstAttempt = sessionOwner.startSignInAttempt()
        assertTrue(firstAttempt != null)

        val result = repository.signIn("test@example.com", "password")

        assertEquals(SignInResult.Busy, result)
    }

    @Test
    fun `signIn returns AuthAdapter error when auth fails`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Failure(SignInResult.InvalidCredentials)

        val result = repository.signIn("invalid@example.com", "wrongpass")

        assertEquals(SignInResult.InvalidCredentials, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns RejectedAuthentication when token lookup fails`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(
                uid = "user-uid",
                email = "user@example.com",
                tokenResult = Result.failure(Exception("Token fetch failed"))
            )
        )

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns MissingProfile on 404 response from profile service`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "user-uid", email = "user@example.com")
        )
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, "".toResponseBody(null))
        )

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.MissingProfile, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns RejectedAuthentication on 401 response from profile service`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "user-uid", email = "user@example.com")
        )
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(401, "".toResponseBody(null))
        )

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns Connectivity on IOException from profile service`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "user-uid", email = "user@example.com")
        )
        fakeProfileApiService.exceptionToThrow = IOException("No internet connection")

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.Connectivity, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns RejectedAuthentication when candidate and profile UIDs mismatch`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "candidate-uid-111", email = "user@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = "different-profile-uid-222",
            email = "user@example.com",
            role = "underwriter"
        )

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns UnsupportedRole when profile role is unknown`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "user-uid", email = "user@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = "user-uid",
            email = "user@example.com",
            role = "super_admin_unsupported"
        )

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.UnsupportedRole, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn returns GenericFailure when partition setup fails`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "user-uid", email = "user@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = "user-uid",
            email = "user@example.com",
            role = "broker"
        )
        fakePartitionManager.shouldSucceed = false

        val result = repository.signIn("user@example.com", "password")

        assertEquals(SignInResult.GenericFailure, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `signIn propagates CancellationException when cancelled`() = runTest {
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = "user-uid", email = "user@example.com")
        )
        fakeProfileApiService.exceptionToThrow = CancellationException("Coroutine cancelled")

        try {
            repository.signIn("user@example.com", "password")
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertEquals("Coroutine cancelled", e.message)
        }

        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `profile 404 failure cleans up attempt via session owner and allows fresh submit retry`() = runTest {
        val candidateUid = "user-uid-123"
        val candidateEmail = "borrower@example.com"
        val initialSignOutCount = fakeAuthAdapter.signOutCount

        // 1st attempt fails with 404
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, "".toResponseBody(null))
        )

        val firstResult = repository.signIn(candidateEmail, "password")
        assertEquals(SignInResult.MissingProfile, firstResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertTrue(fakeAuthAdapter.signOutCount > initialSignOutCount)

        // Fresh submit retry succeeds
        fakeProfileApiService.exceptionToThrow = null
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = "Test Borrower"
        )

        val retryResult = repository.signIn(candidateEmail, "password")
        assertEquals(SignInResult.Success, retryResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.Authenticated)
    }

    @Test
    fun `UID mismatch failure cleans up attempt via session owner and allows fresh submit retry`() = runTest {
        val candidateUid = "candidate-uid-111"
        val mismatchUid = "different-profile-uid-222"
        val email = "user@example.com"
        val initialSignOutCount = fakeAuthAdapter.signOutCount

        // 1st attempt fails with UID mismatch
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = email)
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = mismatchUid,
            email = email,
            role = "underwriter"
        )

        val firstResult = repository.signIn(email, "password")
        assertEquals(SignInResult.RejectedAuthentication, firstResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertTrue(fakeAuthAdapter.signOutCount > initialSignOutCount)

        // Fresh submit retry with matching UID succeeds
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = email,
            role = "underwriter"
        )

        val retryResult = repository.signIn(email, "password")
        assertEquals(SignInResult.Success, retryResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.Authenticated)
    }

    @Test
    fun `unsupported role failure cleans up attempt via session owner and allows fresh submit retry`() = runTest {
        val candidateUid = "user-uid-123"
        val email = "user@example.com"
        val initialSignOutCount = fakeAuthAdapter.signOutCount

        // 1st attempt fails with unknown role
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = email)
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = email,
            role = "invalid_role"
        )

        val firstResult = repository.signIn(email, "password")
        assertEquals(SignInResult.UnsupportedRole, firstResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertTrue(fakeAuthAdapter.signOutCount > initialSignOutCount)

        // Fresh submit retry with supported role succeeds
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = email,
            role = "broker"
        )

        val retryResult = repository.signIn(email, "password")
        assertEquals(SignInResult.Success, retryResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.Authenticated)
    }

    @Test
    fun `transport failure cleans up attempt via session owner and allows fresh submit retry`() = runTest {
        val candidateUid = "user-uid-123"
        val email = "user@example.com"
        val initialSignOutCount = fakeAuthAdapter.signOutCount

        // 1st attempt fails with IOException
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = email)
        )
        fakeProfileApiService.exceptionToThrow = IOException("Connection reset")

        val firstResult = repository.signIn(email, "password")
        assertEquals(SignInResult.Connectivity, firstResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertTrue(fakeAuthAdapter.signOutCount > initialSignOutCount)

        // Fresh submit retry after network returns succeeds
        fakeProfileApiService.exceptionToThrow = null
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = email,
            role = "borrower"
        )

        val retryResult = repository.signIn(email, "password")
        assertEquals(SignInResult.Success, retryResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.Authenticated)
    }

    @Test
    fun `partition setup failure cleans up attempt via session owner and allows fresh submit retry`() = runTest {
        val candidateUid = "user-uid-123"
        val email = "user@example.com"
        val initialSignOutCount = fakeAuthAdapter.signOutCount

        // 1st attempt fails when partition setup fails
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = email)
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = email,
            role = "broker"
        )
        fakePartitionManager.shouldSucceed = false

        val firstResult = repository.signIn(email, "password")
        assertEquals(SignInResult.GenericFailure, firstResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertTrue(fakeAuthAdapter.signOutCount > initialSignOutCount)

        // Fresh submit retry after partition setup succeeds
        fakePartitionManager.shouldSucceed = true

        val retryResult = repository.signIn(email, "password")
        assertEquals(SignInResult.Success, retryResult)
        assertTrue(sessionOwner.sessionState.value is SessionState.Authenticated)
    }

    @Test
    fun `an old failure cleans up only its own attempt and leaves newer attempt active`() = runTest {
        val oldAttempt = sessionOwner.startSignInAttempt()
        assertTrue(oldAttempt != null)

        sessionOwner.cancelAttempt(oldAttempt!!)

        val newAttempt = sessionOwner.startSignInAttempt()
        assertTrue(newAttempt != null)
        val currentStateBefore = sessionOwner.sessionState.value
        assertTrue(currentStateBefore is SessionState.Establishing)
        assertEquals(newAttempt?.attemptId, (currentStateBefore as SessionState.Establishing).attemptId)

        val signOutCountBefore = fakeAuthAdapter.signOutCount

        sessionOwner.cancelAttempt(oldAttempt)

        val currentStateAfter = sessionOwner.sessionState.value
        assertTrue(currentStateAfter is SessionState.Establishing)
        assertEquals(newAttempt?.attemptId, (currentStateAfter as SessionState.Establishing).attemptId)
        assertEquals(signOutCountBefore, fakeAuthAdapter.signOutCount)
    }

    @Test
    fun `restoreSession returns GenericFailure when no stored marker exists`() = runTest {
        val result = repository.restoreSession()

        assertEquals(SignInResult.GenericFailure, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `restoreSession restores session when SDK UID matches marker and profile succeeds`() = runTest {
        val uid = "restored-uid-123"
        fakeStorage.saveCompletedUid(uid)
        fakeAuthAdapter.sdkUid = uid
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = uid,
            email = "restored@example.com",
            role = "broker",
            displayName = "Restored Broker"
        )

        val result = repository.restoreSession()

        assertEquals(SignInResult.Success, result)
        val state = sessionOwner.sessionState.value
        assertTrue(state is SessionState.Authenticated)
        val authState = state as SessionState.Authenticated
        assertEquals(uid, authState.uid)
        assertEquals("restored@example.com", authState.email)
        assertEquals(BackendRole.BROKER, authState.role)
        assertEquals("Restored Broker", authState.displayName)
    }

    @Test
    fun `restoreSession returns RejectedAuthentication and clears marker when SDK UID mismatches marker`() = runTest {
        val storedUid = "stored-uid-123"
        fakeStorage.saveCompletedUid(storedUid)
        fakeAuthAdapter.sdkUid = "different-sdk-uid-456"

        val result = repository.restoreSession()

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `restoreSession handles transient offline restore keeping marker and closing private content`() = runTest {
        val uid = "offline-uid-123"
        fakeStorage.saveCompletedUid(uid)
        fakeAuthAdapter.sdkUid = uid
        fakeProfileApiService.exceptionToThrow = IOException("No internet connection")

        val result = repository.restoreSession()

        assertEquals(SignInResult.Connectivity, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(uid, fakeStorage.getCompletedUid())
    }

    @Test
    fun `restoreSession handles terminal invalid profile 401 by clearing marker and reconciling auth`() = runTest {
        val uid = "invalid-profile-uid"
        fakeStorage.saveCompletedUid(uid)
        fakeAuthAdapter.sdkUid = uid
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(401, "".toResponseBody(null))
        )

        val result = repository.restoreSession()

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `restoreSession handles terminal invalid profile 404 by clearing marker and reconciling auth`() = runTest {
        val uid = "missing-profile-uid"
        fakeStorage.saveCompletedUid(uid)
        fakeAuthAdapter.sdkUid = uid
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, "".toResponseBody(null))
        )

        val result = repository.restoreSession()

        assertEquals(SignInResult.MissingProfile, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `restoreSession handles terminal unsupported role by clearing marker and reconciling auth`() = runTest {
        val uid = "bad-role-uid"
        fakeStorage.saveCompletedUid(uid)
        fakeAuthAdapter.sdkUid = uid
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = uid,
            email = "badrole@example.com",
            role = "unsupported_admin_role"
        )

        val result = repository.restoreSession()

        assertEquals(SignInResult.UnsupportedRole, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `restoreSession returns Busy when another attempt is already active`() = runTest {
        fakeStorage.saveCompletedUid("some-uid")
        fakeAuthAdapter.sdkUid = "some-uid"

        val attempt = sessionOwner.startSignInAttempt()
        assertTrue(attempt != null)

        val result = repository.restoreSession()

        assertEquals(SignInResult.Busy, result)
    }

    @Test
    fun `startSignInAttempt clears old completed marker before starting new credential sign-in`() = runTest {
        fakeStorage.saveCompletedUid("old-uid-999")
        assertEquals("old-uid-999", fakeStorage.getCompletedUid())

        val attempt = sessionOwner.startSignInAttempt()
        assertTrue(attempt != null)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    // Helper fake implementations
    private class TestAuthUser(
        override val uid: String,
        override val email: String?,
        private val tokenResult: Result<String> = Result.success("test-token")
    ) : AuthUser {
        override suspend fun getIdToken(forceRefresh: Boolean): Result<String> = tokenResult
    }

    private class FakeAuthAdapter : AuthAdapter {
        var sdkUid: String? = null
        override val currentUid: String?
            get() = sdkUid
        var signInResult: AuthResult = AuthResult.Failure(SignInResult.InvalidCredentials)
        var signOutCount: Int = 0

        override suspend fun signIn(email: String, password: String): AuthResult {
            val result = signInResult
            if (result is AuthResult.Success) {
                sdkUid = result.user.uid
            }
            return result
        }

        override suspend fun getToken(uid: String, forceRefresh: Boolean): Result<String> {
            if (sdkUid == null || sdkUid != uid) {
                return Result.failure(IllegalStateException("Current SDK user missing or UID mismatch"))
            }
            return Result.success("test-token")
        }

        override suspend fun signOut(): Boolean {
            sdkUid = null
            signOutCount++
            return true
        }
    }

    private class FakeProfileApiService : ProfileApiService {
        var profileResult: UserProfileDto? = null
        var exceptionToThrow: Exception? = null
        var lastHeader: String? = null

        override suspend fun getProfile(bearerToken: String): UserProfileDto {
            lastHeader = bearerToken
            exceptionToThrow?.let { throw it }
            return profileResult ?: throw IllegalStateException("Profile not configured")
        }
    }

    private class FakeUserPartitionManager : UserPartitionManager {
        override var currentPartitionUid: String? = null
        var shouldSucceed: Boolean = true
        val preparedUids = mutableListOf<String>()

        override suspend fun preparePartition(uid: String): Boolean {
            preparedUids.add(uid)
            return if (shouldSucceed) {
                currentPartitionUid = uid
                true
            } else {
                false
            }
        }

        override fun isPartitionReady(uid: String): Boolean = currentPartitionUid == uid

        override suspend fun clearPartition() {
            currentPartitionUid = null
        }
    }

    private class FakeCompletedSessionStorage : CompletedSessionStorage {
        private var savedUid: String? = null

        override suspend fun getCompletedUid(): String? = savedUid

        override suspend fun saveCompletedUid(uid: String): Boolean {
            savedUid = uid
            return true
        }

        override suspend fun clearCompletedUid(): Boolean {
            savedUid = null
            return true
        }
    }
}
