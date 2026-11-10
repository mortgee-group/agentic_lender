package com.mortgee.al_mobile.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.mortgee.al_mobile.data.model.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAdapterTest {

    private class FakeAuthUser(
        override val uid: String,
        override val email: String?,
        private val tokenSupplier: suspend (forceRefresh: Boolean) -> Result<String>
    ) : AuthUser {
        override suspend fun getIdToken(forceRefresh: Boolean): Result<String> {
            return tokenSupplier(forceRefresh)
        }
    }

    private class FakeAuthAdapter : AuthAdapter {
        private val mutationMutex = Mutex()
        var currentUserUid: String? = null
        var currentUserEmail: String? = null
        var signInException: Exception? = null
        var tokenToReturn: String = "token_abc_123"

        // Hook for testing delayed SDK task settlement
        var sdkTaskDeferred: CompletableDeferred<Unit>? = null

        override suspend fun signIn(email: String, password: String): AuthResult {
            return mutationMutex.withLock {
                sdkTaskDeferred?.await()

                val exception = signInException
                if (exception != null) {
                    if (exception is CancellationException) throw exception
                    return@withLock AuthResult.Failure(FirebaseAuthAdapter.mapFirebaseException(exception))
                }

                currentUserUid = "uid_${email.hashCode()}"
                currentUserEmail = email

                val candidateUid = currentUserUid!!
                val user = FakeAuthUser(
                    uid = candidateUid,
                    email = currentUserEmail
                ) { forceRefresh ->
                    if (currentUserUid == candidateUid) {
                        Result.success(tokenToReturn)
                    } else {
                        Result.failure(IllegalStateException("User instance mismatched or stale"))
                    }
                }

                AuthResult.Success(user)
            }
        }

        override suspend fun getToken(uid: String, forceRefresh: Boolean): Result<String> {
            if (currentUserUid == null || currentUserUid != uid) {
                return Result.failure(IllegalStateException("Current SDK user missing or UID mismatch"))
            }
            return Result.success(tokenToReturn)
        }

        override suspend fun signOut(): Boolean {
            return mutationMutex.withLock {
                currentUserUid = null
                currentUserEmail = null
                true
            }
        }
    }

    @Test
    fun `mapFirebaseException maps invalid credentials exceptions`() {
        val invalidCreds = Exception("ERROR_INVALID_CREDENTIAL: Wrong password")
        val invalidUser = Exception("ERROR_USER_NOT_FOUND: User not found")

        assertEquals(SignInResult.InvalidCredentials, FirebaseAuthAdapter.mapFirebaseException(invalidCreds))
        assertEquals(SignInResult.InvalidCredentials, FirebaseAuthAdapter.mapFirebaseException(invalidUser))
    }

    @Test
    fun `mapFirebaseException maps collision or disabled account`() {
        val disabled = Exception("ERROR_USER_DISABLED: User is disabled")
        assertEquals(SignInResult.DisabledAccount, FirebaseAuthAdapter.mapFirebaseException(disabled))
    }

    @Test
    fun `mapFirebaseException maps network exception to connectivity`() {
        val network = Exception("Network connection failed")
        assertEquals(SignInResult.Connectivity, FirebaseAuthAdapter.mapFirebaseException(network))
    }

    @Test
    fun `mapFirebaseException maps unknown exception to generic failure`() {
        val generic = IllegalStateException("Unexpected auth error")
        assertEquals(SignInResult.GenericFailure, FirebaseAuthAdapter.mapFirebaseException(generic))
    }

    @Test
    fun `successful sign in returns AuthResult Success with bound user and token`() = runTest {
        val adapter = FakeAuthAdapter()

        val result = adapter.signIn("borrower@example.com", "password123")
        assertTrue(result.isSuccess)
        assertTrue(result is AuthResult.Success)

        val success = result as AuthResult.Success
        val user = success.user
        assertNotNull(user.uid)
        assertEquals("borrower@example.com", user.email)

        val tokenResult = user.getIdToken()
        assertTrue(tokenResult.isSuccess)
        assertEquals("token_abc_123", tokenResult.getOrNull())
    }

    @Test
    fun `getToken returns token for matching candidate UID and fails for mismatched UID`() = runTest {
        val adapter = FakeAuthAdapter()
        val result = adapter.signIn("broker@example.com", "pass") as AuthResult.Success
        val uid = result.user.uid

        val validToken = adapter.getToken(uid)
        assertTrue(validToken.isSuccess)
        assertEquals("token_abc_123", validToken.getOrNull())

        val invalidToken = adapter.getToken("wrong_uid_999")
        assertFalse(invalidToken.isSuccess)
    }

    @Test
    fun `failed sign in returns AuthResult Failure with mapped error`() = runTest {
        val adapter = FakeAuthAdapter().apply {
            signInException = Exception("wrong password")
        }

        val result = adapter.signIn("test@example.com", "wrongpass")
        assertTrue(result is AuthResult.Failure)
        val failure = result as AuthResult.Failure
        assertEquals(SignInResult.InvalidCredentials, failure.error)
    }

    @Test
    fun `mutation lane serializes sign in operations`() = runTest {
        val adapter = FakeAuthAdapter()
        val deferred = CompletableDeferred<Unit>()
        adapter.sdkTaskDeferred = deferred

        val job1 = launch {
            adapter.signIn("userA@example.com", "passA")
        }

        testScheduler.advanceUntilIdle()

        var job2Finished = false
        val job2 = launch {
            adapter.signIn("userB@example.com", "passB")
            job2Finished = true
        }

        testScheduler.advanceUntilIdle()
        assertFalse(job2Finished)

        deferred.complete(Unit)
        testScheduler.advanceUntilIdle()

        job1.join()
        job2.join()
        assertTrue(job2Finished)
        assertEquals("userB@example.com", adapter.currentUserEmail)
    }

    @Test
    fun `signOut clears active candidate identity`() = runTest {
        val adapter = FakeAuthAdapter()
        adapter.signIn("test@example.com", "password")

        val signedOut = adapter.signOut()
        assertTrue(signedOut)
        assertEquals(null, adapter.currentUserUid)
    }
}
