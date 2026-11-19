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
            displayName = "Test Borrower",
            firstName = "John",
            lastName = "Conner",
            phoneNumber = "+353871234567",
            createdAt = "2026-11-06T10:00:00Z",
            updatedAt = "2026-11-06T10:00:00Z"
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
        assertEquals("John", authState.firstName)
        assertEquals("Conner", authState.lastName)
        assertEquals("+353871234567", authState.phoneNumber)
        assertEquals("2026-11-06T10:00:00Z", authState.createdAt)
        assertEquals("2026-11-06T10:00:00Z", authState.updatedAt)
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
    fun `stale invalidated attempt does not prepare partition or overwrite newer partition`() = runTest {
        val staleUid = "stale-uid-111"
        val newerUid = "newer-uid-222"

        // Pre-populate active partition for a newer user during in-flight work
        fakePartitionManager.preparePartition(newerUid)
        fakePartitionManager.preparedUids.clear()

        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = staleUid, email = "stale@example.com")
        )
        fakeProfileApiService.onGetProfile = {
            // Simulate attempt being invalidated while profile GET is in flight
            sessionOwner.invalidateSession()
        }
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = staleUid,
            email = "stale@example.com",
            role = "borrower",
            displayName = "Stale Borrower"
        )

        val result = repository.signIn("stale@example.com", "password")

        assertEquals(SignInResult.GenericFailure, result)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertEquals(newerUid, fakePartitionManager.currentPartitionUid)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
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

    @Test
    fun `register executes one Firebase create and one profile POST and commits authenticated Borrower session on valid 201`() = runTest {
        val candidateUid = "new-borrower-uid-123"
        val candidateEmail = "new.borrower@example.com"
        val candidateToken = "new-firebase-id-token-abc"
        val displayName = "Test Borrower"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success(candidateToken))
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = displayName,
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "+1234567890",
            createdAt = "2026-11-19T10:00:00Z",
            updatedAt = "2026-11-19T10:00:00Z"
        )

        val result = repository.register(candidateEmail, "StrongPass123!", displayName)

        assertEquals(SignInResult.Success, result)
        assertEquals(1, fakeAuthAdapter.createAccountCount)
        assertEquals(0, fakeAuthAdapter.signInCount)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals(0, fakeProfileApiService.getProfileCount)
        assertEquals("Bearer $candidateToken", fakeProfileApiService.lastHeader)
        assertEquals(
            com.mortgee.al_mobile.data.network.model.CreateProfileRequest(displayName = displayName),
            fakeProfileApiService.lastCreatedRequest
        )
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)
        assertEquals(candidateUid, fakeStorage.getCompletedUid())

        val currentState = sessionOwner.sessionState.value
        assertTrue(currentState is SessionState.Authenticated)
        val authState = currentState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(candidateEmail, authState.email)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals(displayName, authState.displayName)
    }

    @Test
    fun `register returns Busy without calling Firebase or profile API when session owner cannot acquire attempt`() = runTest {
        val activeAttempt = sessionOwner.startSignInAttempt()
        assertTrue(activeAttempt != null)

        val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.Busy, result)
        assertEquals(0, fakeAuthAdapter.createAccountCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
    }

    @Test
    fun `register keeps access closed and returns RejectedAuthentication when returned profile UID mismatches candidate`() = runTest {
        val candidateUid = "candidate-uid-111"
        val wrongProfileUid = "wrong-profile-uid-222"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = wrongProfileUid,
            email = "borrower@example.com",
            role = "borrower",
            displayName = "Test Borrower"
        )

        val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertEquals(1, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `register keeps access closed and returns UnsupportedRole when profile role is absent`() = runTest {
        val candidateUid = "candidate-uid-123"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = "borrower@example.com",
            role = null,
            displayName = "Test Borrower"
        )

        val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.UnsupportedRole, result)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `register keeps access closed and returns UnsupportedRole when profile role is non-Borrower`() = runTest {
        val candidateUid = "candidate-uid-123"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "broker@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = "broker@example.com",
            role = "broker",
            displayName = "Unexpected Broker"
        )

        val result = repository.register("broker@example.com", "StrongPass123!", "Unexpected Broker")

        assertEquals(SignInResult.UnsupportedRole, result)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `register keeps access closed and returns GenericFailure when partition preparation fails`() = runTest {
        val candidateUid = "candidate-uid-123"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = "borrower@example.com",
            role = "borrower",
            displayName = "Test Borrower"
        )
        fakePartitionManager.shouldSucceed = false

        val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.GenericFailure, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `register keeps access closed and returns GenericFailure when session commit fails due to marker storage failure`() = runTest {
        val candidateUid = "candidate-uid-123"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = "borrower@example.com",
            role = "borrower",
            displayName = "Test Borrower"
        )
        fakeStorage.shouldSaveSucceed = false

        val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.GenericFailure, result)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `register does not prepare partition or commit when attempt is invalidated during profile POST`() = runTest {
        val staleUid = "stale-register-uid-111"
        val newerUid = "newer-uid-222"

        fakePartitionManager.preparePartition(newerUid)
        fakePartitionManager.preparedUids.clear()

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = staleUid, email = "stale@example.com")
        )
        fakeProfileApiService.onCreateProfile = {
            sessionOwner.invalidateSession()
        }
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = staleUid,
            email = "stale@example.com",
            role = "borrower",
            displayName = "Stale Borrower"
        )

        val result = repository.register("stale@example.com", "StrongPass123!", "Stale Borrower")

        assertEquals(SignInResult.GenericFailure, result)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertEquals(newerUid, fakePartitionManager.currentPartitionUid)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `register maps AuthAdapter failures with zero profile POST calls and cleans up attempt`() = runTest {
        val adapterErrors = listOf(
            SignInResult.InvalidEmail,
            SignInResult.WeakPassword,
            SignInResult.AccountExists,
            SignInResult.Connectivity,
            SignInResult.DisabledAccount,
            SignInResult.GenericFailure,
        )

        for (expectedError in adapterErrors) {
            fakeAuthAdapter.createAccountResult = AuthResult.Failure(expectedError)
            val createCountBefore = fakeProfileApiService.createProfileCount

            val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

            assertEquals(expectedError, result)
            assertEquals(createCountBefore, fakeProfileApiService.createProfileCount)
            assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
            assertEquals(null, fakeStorage.getCompletedUid())
        }
    }

    @Test
    fun `register returns RejectedAuthentication and cleans up candidate when token lookup fails`() = runTest {
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(
                uid = "candidate-uid-123",
                email = "borrower@example.com",
                tokenResult = Result.failure(IOException("Failed to fetch ID token"))
            )
        )

        val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.RejectedAuthentication, result)
        assertEquals(1, fakeAuthAdapter.createAccountCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `register maps POST 400, 401, and 403 to RejectedAuthentication with zero replays and cleans up candidate`() = runTest {
        for (statusCode in listOf(400, 401, 403)) {
            fakeAuthAdapter.createAccountResult = AuthResult.Success(
                TestAuthUser(uid = "candidate-uid-$statusCode", email = "borrower@example.com")
            )
            fakeProfileApiService.exceptionToThrow = HttpException(
                Response.error<UserProfileDto>(statusCode, """{"detail":"Error $statusCode"}""".toResponseBody(null))
            )
            val postCountBefore = fakeProfileApiService.createProfileCount
            val signOutBefore = fakeAuthAdapter.signOutCount

            val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

            assertEquals("Status $statusCode should map to RejectedAuthentication", SignInResult.RejectedAuthentication, result)
            assertEquals(postCountBefore + 1, fakeProfileApiService.createProfileCount)
            assertEquals(0, fakeProfileApiService.getProfileCount)
            assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
            assertEquals(null, fakeAuthAdapter.currentUid)
            assertTrue(fakeAuthAdapter.signOutCount > signOutBefore)
            assertEquals(null, fakeStorage.getCompletedUid())
        }
    }

    @Test
    fun `register maps POST 422 with verified display_name field identifier to InvalidDisplayName without replay`() = runTest {
        val verifiedBodies = listOf(
            """{"detail":[{"loc":["body","display_name"],"msg":"Field required","type":"missing"}]}""",
            """{"errors":{"display_name":["Display name must not be blank"]}}""",
            """{"field":"displayName","message":"Invalid display name"}""",
        )

        for (body in verifiedBodies) {
            fakeAuthAdapter.createAccountResult = AuthResult.Success(
                TestAuthUser(uid = "candidate-uid-422", email = "borrower@example.com")
            )
            fakeProfileApiService.exceptionToThrow = HttpException(
                Response.error<UserProfileDto>(422, body.toResponseBody(null))
            )
            val postCountBefore = fakeProfileApiService.createProfileCount
            val signOutBefore = fakeAuthAdapter.signOutCount

            val result = repository.register("borrower@example.com", "StrongPass123!", "   ")

            assertEquals("Body $body should map to InvalidDisplayName", SignInResult.InvalidDisplayName, result)
            assertEquals(postCountBefore + 1, fakeProfileApiService.createProfileCount)
            assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
            assertEquals(null, fakeAuthAdapter.currentUid)
            assertTrue(fakeAuthAdapter.signOutCount > signOutBefore)
        }
    }

    @Test
    fun `register maps POST 422 with unknown field or malformed error body to GenericFailure`() = runTest {
        val unverifiedBodies = listOf(
            """{"detail":[{"loc":["body","phone_number"],"msg":"Invalid phone"}]}""",
            """{"detail":"Unprocessable entity"}""",
            """not valid json at all""",
            """""",
        )

        for (body in unverifiedBodies) {
            fakeAuthAdapter.createAccountResult = AuthResult.Success(
                TestAuthUser(uid = "candidate-uid-422-unverified", email = "borrower@example.com")
            )
            fakeProfileApiService.exceptionToThrow = HttpException(
                Response.error<UserProfileDto>(422, body.toResponseBody(null))
            )
            val postCountBefore = fakeProfileApiService.createProfileCount
            val signOutBefore = fakeAuthAdapter.signOutCount

            val result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

            assertEquals("Body '$body' should map to GenericFailure", SignInResult.GenericFailure, result)
            assertEquals(postCountBefore + 1, fakeProfileApiService.createProfileCount)
            assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
            assertEquals(null, fakeAuthAdapter.currentUid)
            assertTrue(fakeAuthAdapter.signOutCount > signOutBefore)
        }
    }

    @Test
    fun `register maps POST 409, 5xx, and IOException after Firebase account creation to CompletionNeeded with zero POST replays`() = runTest {
        // 1. HTTP 409 Conflict
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = "candidate-uid-409", email = "borrower@example.com")
        )
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(409, """{"detail":"Profile already exists"}""".toResponseBody(null))
        )

        val conflictResult = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.CompletionNeeded, conflictResult)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)

        // 2. HTTP 503 Service Unavailable
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = "candidate-uid-503", email = "borrower@example.com")
        )
        fakeProfileApiService.exceptionToThrow = HttpException(
            Response.error<UserProfileDto>(503, """{"detail":"Service unavailable"}""".toResponseBody(null))
        )

        val serverErrorResult = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.CompletionNeeded, serverErrorResult)
        assertEquals(2, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)

        // 3. IOException transport failure during POST
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = "candidate-uid-io", email = "borrower@example.com")
        )
        fakeProfileApiService.exceptionToThrow = IOException("Connection reset during profile POST")

        val ioResult = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.CompletionNeeded, ioResult)
        assertEquals(3, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `register propagates CancellationException and cleans up attempt`() = runTest {
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = "candidate-uid-cancel", email = "borrower@example.com")
        )
        fakeProfileApiService.exceptionToThrow = CancellationException("Registration cancelled")

        try {
            repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertEquals("Registration cancelled", e.message)
        }

        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
        assertTrue(fakeAuthAdapter.signOutCount > 0)
    }

    @Test
    fun `register reconciles POST 409 followed by matching GET 200 Borrower profile and commits session with one logical read and zero additional POSTs`() = runTest {
        val candidateUid = "reconciled-409-uid"
        val candidateEmail = "reconciled409@example.com"
        val candidateToken = "reconciled-409-token"
        val displayName = "Reconciled Borrower"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success(candidateToken))
        )
        fakeProfileApiService.createExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(409, """{"detail":"Profile already exists"}""".toResponseBody(null))
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = displayName,
            firstName = "Reconciled",
            lastName = "Borrower",
            phoneNumber = "+1234567890",
            createdAt = "2026-11-19T12:00:00Z",
            updatedAt = "2026-11-19T12:00:00Z"
        )

        val result = repository.register(candidateEmail, "StrongPass123!", displayName)

        assertEquals(SignInResult.Success, result)
        assertEquals(1, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals("Bearer $candidateToken", fakeProfileApiService.lastHeader)
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)
        assertEquals(candidateUid, fakeStorage.getCompletedUid())

        val currentState = sessionOwner.sessionState.value
        assertTrue(currentState is SessionState.Authenticated)
        val authState = currentState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(candidateEmail, authState.email)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals(displayName, authState.displayName)
    }

    @Test
    fun `register reconciles POST timeout followed by matching GET 200 Borrower profile and commits session with one logical read and zero additional POSTs`() = runTest {
        val candidateUid = "reconciled-timeout-uid"
        val candidateEmail = "reconciled.timeout@example.com"
        val candidateToken = "reconciled-timeout-token"
        val displayName = "Timeout Reconciled Borrower"

        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success(candidateToken))
        )
        fakeProfileApiService.createExceptionToThrow = java.net.SocketTimeoutException("Read timed out during POST /users/me")
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = displayName,
            createdAt = "2026-11-19T12:05:00Z",
            updatedAt = "2026-11-19T12:05:00Z"
        )

        val result = repository.register(candidateEmail, "StrongPass123!", displayName)

        assertEquals(SignInResult.Success, result)
        assertEquals(1, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals("Bearer $candidateToken", fakeProfileApiService.lastHeader)
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)
        assertEquals(candidateUid, fakeStorage.getCompletedUid())

        val currentState = sessionOwner.sessionState.value
        assertTrue(currentState is SessionState.Authenticated)
        val authState = currentState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals(displayName, authState.displayName)
    }

    @Test
    fun `register reconciles POST 409 and timeout followed by GET 404 by closing candidate and returning CompletionNeeded with one logical read and zero additional POSTs`() = runTest {
        // 1. POST 409 followed by GET 404
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = "uid-409-then-404", email = "borrower@example.com")
        )
        fakeProfileApiService.createExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(409, """{"detail":"Conflict"}""".toResponseBody(null))
        )
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, """{"detail":"Not found"}""".toResponseBody(null))
        )
        val signOutBefore409 = fakeAuthAdapter.signOutCount

        val conflict404Result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.CompletionNeeded, conflict404Result)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
        assertTrue(fakeAuthAdapter.signOutCount > signOutBefore409)
        assertEquals(null, fakeStorage.getCompletedUid())

        // 2. POST timeout followed by GET 404
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = "uid-timeout-then-404", email = "borrower@example.com")
        )
        fakeProfileApiService.createExceptionToThrow = java.net.SocketTimeoutException("Read timed out")
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, """{"detail":"Not found"}""".toResponseBody(null))
        )
        val signOutBeforeTimeout = fakeAuthAdapter.signOutCount

        val timeout404Result = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")

        assertEquals(SignInResult.CompletionNeeded, timeout404Result)
        assertEquals(2, fakeProfileApiService.createProfileCount)
        assertEquals(2, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
        assertTrue(fakeAuthAdapter.signOutCount > signOutBeforeTimeout)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `register reconciliation rejects GET 200 when returned UID mismatches, role is missing, or role is non-Borrower and keeps access closed`() = runTest {
        val candidateUid = "candidate-reconcile-uid"
        fakeProfileApiService.createExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(409, """{"detail":"Conflict"}""".toResponseBody(null))
        )

        // 1. Mismatched UID on reconciliation GET 200
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = "other-user-uid",
            email = "borrower@example.com",
            role = "borrower",
            displayName = "Test Borrower"
        )

        val mismatchResult = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.RejectedAuthentication, mismatchResult)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())

        // 2. Missing role on reconciliation GET 200
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = "borrower@example.com",
            role = null,
            displayName = "Test Borrower"
        )

        val missingRoleResult = repository.register("borrower@example.com", "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.UnsupportedRole, missingRoleResult)
        assertEquals(2, fakeProfileApiService.createProfileCount)
        assertEquals(2, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())

        // 3. Non-Borrower role on reconciliation GET 200
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = "borrower@example.com")
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = "borrower@example.com",
            role = "broker",
            displayName = "Unexpected Broker"
        )

        val nonBorrowerResult = repository.register("borrower@example.com", "StrongPass123!", "Unexpected Broker")
        assertEquals(SignInResult.UnsupportedRole, nonBorrowerResult)
        assertEquals(3, fakeProfileApiService.createProfileCount)
        assertEquals(3, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `register reconciliation aborts on stale completion during POST or GET without committing or overwriting newer partition`() = runTest {
        val staleUid = "stale-reconcile-uid"
        val newerUid = "newer-active-uid"

        fakePartitionManager.preparePartition(newerUid)
        fakePartitionManager.preparedUids.clear()

        // 1. Attempt invalidated while reconciliation GET 200 is in flight after POST timeout
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = staleUid, email = "stale@example.com")
        )
        fakeProfileApiService.createExceptionToThrow = java.net.SocketTimeoutException("Read timed out")
        fakeProfileApiService.onGetProfile = {
            sessionOwner.invalidateSession()
        }
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = staleUid,
            email = "stale@example.com",
            role = "borrower",
            displayName = "Stale Borrower"
        )

        val staleGetResult = repository.register("stale@example.com", "StrongPass123!", "Stale Borrower")

        assertEquals(SignInResult.GenericFailure, staleGetResult)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertEquals(newerUid, fakePartitionManager.currentPartitionUid)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())

        // 2. Attempt invalidated during POST before 409 is thrown skips reconciliation GET completely
        fakeProfileApiService.onGetProfile = null
        fakeProfileApiService.createExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(409, """{"detail":"Conflict"}""".toResponseBody(null))
        )
        fakeProfileApiService.onCreateProfile = {
            sessionOwner.invalidateSession()
        }
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = staleUid, email = "stale@example.com")
        )

        val stalePostResult = repository.register("stale@example.com", "StrongPass123!", "Stale Borrower")

        assertEquals(SignInResult.GenericFailure, stalePostResult)
        assertEquals(2, fakeProfileApiService.createProfileCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertEquals(newerUid, fakePartitionManager.currentPartitionUid)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())
    }

    @Test
    fun `completeRegistration authenticates once, reads existing Borrower profile, and commits session with zero POSTs`() = runTest {
        val candidateUid = "existing-borrower-uid"
        val candidateEmail = "existing.borrower@example.com"
        val candidateToken = "existing-borrower-token"
        val displayName = "Existing Borrower"

        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success(candidateToken))
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = displayName,
            firstName = "Existing",
            lastName = "Borrower",
            phoneNumber = "+1234567890",
            createdAt = "2026-11-19T13:00:00Z",
            updatedAt = "2026-11-19T13:00:00Z"
        )

        val result = repository.completeRegistration(candidateEmail, "StrongPass123!", displayName)

        assertEquals(SignInResult.Success, result)
        assertEquals(1, fakeAuthAdapter.signInCount)
        assertEquals(0, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertEquals("Bearer $candidateToken", fakeProfileApiService.lastHeader)
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)
        assertEquals(candidateUid, fakeStorage.getCompletedUid())

        val currentState = sessionOwner.sessionState.value
        assertTrue(currentState is SessionState.Authenticated)
        val authState = currentState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(candidateEmail, authState.email)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals(displayName, authState.displayName)
    }

    @Test
    fun `completeRegistration rejects non-Borrower, missing role, or mismatched UID without creating or overwriting a profile`() = runTest {
        val candidateUid = "existing-user-uid"
        val candidateEmail = "user@example.com"

        // 1. Existing Broker profile returns UnsupportedRole with zero POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "broker",
            displayName = "Existing Broker"
        )
        val signOutBeforeBroker = fakeAuthAdapter.signOutCount

        val brokerResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Overwrite Attempt")

        assertEquals(SignInResult.UnsupportedRole, brokerResult)
        assertEquals(1, fakeAuthAdapter.signInCount)
        assertEquals(0, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
        assertTrue(fakeAuthAdapter.signOutCount > signOutBeforeBroker)
        assertEquals(null, fakeStorage.getCompletedUid())

        // 2. Existing Underwriter profile returns UnsupportedRole with zero POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "underwriter",
            displayName = "Existing Underwriter"
        )

        val underwriterResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Overwrite Attempt")

        assertEquals(SignInResult.UnsupportedRole, underwriterResult)
        assertEquals(2, fakeAuthAdapter.signInCount)
        assertEquals(2, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)

        // 3. Missing role returns UnsupportedRole with zero POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = null,
            displayName = "Missing Role User"
        )

        val missingRoleResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Overwrite Attempt")

        assertEquals(SignInResult.UnsupportedRole, missingRoleResult)
        assertEquals(3, fakeAuthAdapter.signInCount)
        assertEquals(3, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)

        // 4. Mismatched UID returns RejectedAuthentication with zero POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getProfileResult = UserProfileDto(
            uid = "mismatched-other-uid",
            email = candidateEmail,
            role = "borrower",
            displayName = "Other Borrower"
        )

        val mismatchResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Overwrite Attempt")

        assertEquals(SignInResult.RejectedAuthentication, mismatchResult)
        assertEquals(4, fakeAuthAdapter.signInCount)
        assertEquals(4, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun `completeRegistration ends attempt on auth failure, token failure, or profile read errors with zero profile writes`() = runTest {
        val candidateUid = "completion-read-error-uid"
        val candidateEmail = "borrower@example.com"

        // 1. Auth signIn failure -> InvalidCredentials, 0 GETs, 0 POSTs
        fakeAuthAdapter.signInResult = AuthResult.Failure(SignInResult.InvalidCredentials)
        val authFailResult = repository.completeRegistration(candidateEmail, "WrongPass", "Test Borrower")
        assertEquals(SignInResult.InvalidCredentials, authFailResult)
        assertEquals(1, fakeAuthAdapter.signInCount)
        assertEquals(0, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)

        // 2. Token retrieval failure -> RejectedAuthentication, 0 GETs, 0 POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.failure(IOException("Token error")))
        )
        val tokenFailResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.RejectedAuthentication, tokenFailResult)
        assertEquals(2, fakeAuthAdapter.signInCount)
        assertEquals(0, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)

        // 3. GET 401 -> RejectedAuthentication, 1 GET, 0 POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(401, """{"detail":"Unauthorized"}""".toResponseBody(null))
        )
        val http401Result = repository.completeRegistration(candidateEmail, "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.RejectedAuthentication, http401Result)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)

        // 4. GET 500 -> GenericFailure, 2 GETs, 0 POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(500, """{"detail":"Internal Server Error"}""".toResponseBody(null))
        )
        val http500Result = repository.completeRegistration(candidateEmail, "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.GenericFailure, http500Result)
        assertEquals(2, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)

        // 5. GET IOException -> Connectivity, 3 GETs, 0 POSTs
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getExceptionToThrow = IOException("Connection reset")
        val ioResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Test Borrower")
        assertEquals(SignInResult.Connectivity, ioResult)
        assertEquals(3, fakeProfileApiService.getProfileCount)
        assertEquals(0, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)
    }

    @Test
    fun `completeRegistration on confirmed GET 404 creates missing Borrower profile with one POST and commits session`() = runTest {
        val candidateUid = "pre-death-created-uid"
        val candidateEmail = "partial.borrower@example.com"
        val candidateToken = "post-restart-signin-token"
        val displayName = "Recovered Borrower"

        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success(candidateToken))
        )
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, """{"detail":"Profile not found"}""".toResponseBody(null))
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = displayName,
            firstName = "Recovered",
            lastName = "Borrower",
            phoneNumber = "+1234567890",
            createdAt = "2026-11-19T14:00:00Z",
            updatedAt = "2026-11-19T14:00:00Z"
        )

        val result = repository.completeRegistration(candidateEmail, "StrongPass123!", displayName)

        assertEquals(SignInResult.Success, result)
        assertEquals(1, fakeAuthAdapter.signInCount)
        assertEquals(0, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertEquals("Bearer $candidateToken", fakeProfileApiService.lastHeader)
        assertEquals(
            com.mortgee.al_mobile.data.network.model.CreateProfileRequest(displayName = displayName),
            fakeProfileApiService.lastCreatedRequest
        )
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)
        assertEquals(candidateUid, fakeStorage.getCompletedUid())

        val currentState = sessionOwner.sessionState.value
        assertTrue(currentState is SessionState.Authenticated)
        val authState = currentState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(candidateEmail, authState.email)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals(displayName, authState.displayName)
    }

    @Test
    fun `completeRegistration recovers account created before process death whose profile is still missing`() = runTest {
        val candidateUid = "account-before-process-death-uid"
        val candidateEmail = "interrupted@example.com"
        val displayName = "Borrower After Restart"

        // Step 1: Initial registration creates Firebase account, but profile POST is interrupted (or process dies before profile exists)
        fakeAuthAdapter.createAccountResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success("initial-token"))
        )
        fakeProfileApiService.createExceptionToThrow = IOException("Process died or connection dropped before POST reached server")
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, """{"detail":"Not found"}""".toResponseBody(null))
        )

        val interruptedResult = repository.register(candidateEmail, "StrongPass123!", displayName)
        assertEquals(SignInResult.CompletionNeeded, interruptedResult)
        assertEquals(1, fakeAuthAdapter.createAccountCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeStorage.getCompletedUid())

        // Step 2: Simulate fresh process after process death (new DefaultSessionOwner with empty marker storage)
        val postRestartSessionOwner = DefaultSessionOwner(fakeStorage, fakeAuthAdapter)
        val postRestartRepository = ApiAccountRepository(
            authAdapter = fakeAuthAdapter,
            profileApiService = fakeProfileApiService,
            sessionOwner = postRestartSessionOwner,
            userPartitionManager = fakePartitionManager,
            completedSessionStorage = fakeStorage
        )

        // Restore on startup fails because no completed marker was ever saved before process death
        val restoreResult = postRestartRepository.restoreSession()
        assertEquals(SignInResult.GenericFailure, restoreResult)
        assertTrue(postRestartSessionOwner.sessionState.value is SessionState.SignedOut)

        // Step 3: User chooses explicit completion ("Finish registration") and submits credentials + displayName.
        // GET /users/me returns 404 (profile still missing), allowing one POST /users/me that succeeds with 201.
        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail, tokenResult = Result.success("fresh-signin-token"))
        )
        fakeProfileApiService.createExceptionToThrow = null
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, """{"detail":"Profile still missing"}""".toResponseBody(null))
        )
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "borrower",
            displayName = displayName,
            createdAt = "2026-11-19T14:10:00Z",
            updatedAt = "2026-11-19T14:10:00Z"
        )

        val createAccountCountBeforeCompletion = fakeAuthAdapter.createAccountCount
        val getCountBeforeCompletion = fakeProfileApiService.getProfileCount
        val postCountBeforeCompletion = fakeProfileApiService.createProfileCount

        val completionResult = postRestartRepository.completeRegistration(candidateEmail, "StrongPass123!", displayName)

        assertEquals(SignInResult.Success, completionResult)
        assertEquals(createAccountCountBeforeCompletion, fakeAuthAdapter.createAccountCount)
        assertEquals(1, fakeAuthAdapter.signInCount)
        assertEquals(getCountBeforeCompletion + 1, fakeProfileApiService.getProfileCount)
        assertEquals(postCountBeforeCompletion + 1, fakeProfileApiService.createProfileCount)
        assertEquals(
            com.mortgee.al_mobile.data.network.model.CreateProfileRequest(displayName = displayName),
            fakeProfileApiService.lastCreatedRequest
        )
        assertEquals(listOf(candidateUid), fakePartitionManager.preparedUids)
        assertEquals(candidateUid, fakeStorage.getCompletedUid())

        val finalState = postRestartSessionOwner.sessionState.value
        assertTrue(finalState is SessionState.Authenticated)
        val authState = finalState as SessionState.Authenticated
        assertEquals(candidateUid, authState.uid)
        assertEquals(BackendRole.BORROWER, authState.role)
        assertEquals(displayName, authState.displayName)
    }

    @Test
    fun `completeRegistration on confirmed GET 404 reuses registration POST validation and error handling without replay`() = runTest {
        val candidateUid = "completion-404-post-error-uid"
        val candidateEmail = "borrower@example.com"

        fakeAuthAdapter.signInResult = AuthResult.Success(
            TestAuthUser(uid = candidateUid, email = candidateEmail)
        )
        fakeProfileApiService.getExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(404, """{"detail":"Not found"}""".toResponseBody(null))
        )

        // 1. POST 422 with verified display_name field -> InvalidDisplayName, 1 GET, 1 POST
        fakeProfileApiService.createExceptionToThrow = HttpException(
            Response.error<UserProfileDto>(
                422,
                """{"detail":[{"loc":["body","display_name"],"msg":"String should have at least 1 character"}]}""".toResponseBody(null)
            )
        )
        val invalidNameResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "   ")
        assertEquals(SignInResult.InvalidDisplayName, invalidNameResult)
        assertEquals(1, fakeProfileApiService.getProfileCount)
        assertEquals(1, fakeProfileApiService.createProfileCount)
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
        assertEquals(null, fakeAuthAdapter.currentUid)

        // 2. POST returns mismatched role -> UnsupportedRole, 2 GETs, 2 POSTs
        fakeProfileApiService.createExceptionToThrow = null
        fakeProfileApiService.profileResult = UserProfileDto(
            uid = candidateUid,
            email = candidateEmail,
            role = "broker",
            displayName = "Unexpected Broker"
        )
        val roleMismatchResult = repository.completeRegistration(candidateEmail, "StrongPass123!", "Unexpected Broker")
        assertEquals(SignInResult.UnsupportedRole, roleMismatchResult)
        assertEquals(2, fakeProfileApiService.getProfileCount)
        assertEquals(2, fakeProfileApiService.createProfileCount)
        assertTrue(fakePartitionManager.preparedUids.isEmpty())
        assertTrue(sessionOwner.sessionState.value is SessionState.SignedOut)
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
        var createAccountResult: AuthResult = AuthResult.Failure(SignInResult.GenericFailure)
        var signOutCount: Int = 0
        var signInCount: Int = 0
        var createAccountCount: Int = 0

        override suspend fun signIn(email: String, password: String): AuthResult {
            signInCount++
            val result = signInResult
            if (result is AuthResult.Success) {
                sdkUid = result.user.uid
            }
            return result
        }

        override suspend fun createAccount(email: String, password: String): AuthResult {
            createAccountCount++
            val result = createAccountResult
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
        var getProfileResult: UserProfileDto? = null
        var exceptionToThrow: Exception? = null
        var createExceptionToThrow: Exception? = null
        var getExceptionToThrow: Exception? = null
        var lastHeader: String? = null
        var lastCreatedRequest: com.mortgee.al_mobile.data.network.model.CreateProfileRequest? = null
        var getProfileCount: Int = 0
        var createProfileCount: Int = 0
        var onGetProfile: (suspend () -> Unit)? = null
        var onCreateProfile: (suspend () -> Unit)? = null

        override suspend fun getProfile(bearerToken: String): UserProfileDto {
            getProfileCount++
            lastHeader = bearerToken
            onGetProfile?.invoke()
            (getExceptionToThrow ?: exceptionToThrow)?.let { throw it }
            return (getProfileResult ?: profileResult) ?: throw IllegalStateException("Profile not configured")
        }

        override suspend fun createProfile(
            bearerToken: String,
            request: com.mortgee.al_mobile.data.network.model.CreateProfileRequest
        ): UserProfileDto {
            createProfileCount++
            lastHeader = bearerToken
            lastCreatedRequest = request
            onCreateProfile?.invoke()
            (createExceptionToThrow ?: exceptionToThrow)?.let { throw it }
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
        var shouldSaveSucceed: Boolean = true

        override suspend fun getCompletedUid(): String? = savedUid

        override suspend fun saveCompletedUid(uid: String): Boolean {
            if (!shouldSaveSucceed) return false
            savedUid = uid
            return true
        }

        override suspend fun clearCompletedUid(): Boolean {
            savedUid = null
            return true
        }
    }
}
