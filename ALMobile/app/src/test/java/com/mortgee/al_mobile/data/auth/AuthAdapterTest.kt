package com.mortgee.al_mobile.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.mortgee.al_mobile.data.model.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        override val currentUid: String?
            get() = currentUserUid
        var currentUserEmail: String? = null
        var signInException: Exception? = null
        var createAccountException: Exception? = null
        var tokenToReturn: String = "token_abc_123"
        var reconciledCancelledAttempts: Int = 0

        // Hook for testing delayed SDK task settlement
        var sdkTaskDeferred: CompletableDeferred<Unit>? = null

        override suspend fun signIn(email: String, password: String): AuthResult {
            return executeMutation(
                email = email,
                forCreate = false,
                exceptionProvider = { signInException }
            )
        }

        override suspend fun createAccount(email: String, password: String): AuthResult {
            return executeMutation(
                email = email,
                forCreate = true,
                exceptionProvider = { createAccountException }
            )
        }

        private suspend fun executeMutation(
            email: String,
            forCreate: Boolean,
            exceptionProvider: () -> Exception?
        ): AuthResult {
            return mutationMutex.withLock {
                val deferred = sdkTaskDeferred
                try {
                    deferred?.await()
                } catch (e: CancellationException) {
                    // Simulate FirebaseAuthAdapter waiting for the underlying SDK task to settle
                    // and reconciling the SDK user state before releasing mutationMutex.
                    withContext(NonCancellable) {
                        runCatching { deferred?.await() }
                        if (exceptionProvider() == null) {
                            currentUserUid = "uid_${email.hashCode()}"
                            currentUserEmail = email
                        }
                        if (currentUserUid != null) {
                            currentUserUid = null
                            currentUserEmail = null
                            reconciledCancelledAttempts++
                        }
                    }
                    throw e
                }

                val exception = exceptionProvider()
                if (exception != null) {
                    if (exception is CancellationException) throw exception
                    val mapped = if (forCreate) {
                        FirebaseAuthAdapter.mapCreateAccountException(exception)
                    } else {
                        FirebaseAuthAdapter.mapFirebaseException(exception)
                    }
                    return@withLock AuthResult.Failure(mapped)
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
    fun `mapCreateAccountException maps collision weak password and invalid email`() {
        val collision = Exception("ERROR_EMAIL_ALREADY_IN_USE: The email address is already in use by another account")
        val weakPassword = Exception("ERROR_WEAK_PASSWORD: The given password is invalid. [ Password should be at least 6 characters ]")
        val invalidEmail = Exception("ERROR_INVALID_EMAIL: The email address is badly formatted")
        val networkError = Exception("Network connection failed")

        assertEquals(SignInResult.AccountExists, FirebaseAuthAdapter.mapCreateAccountException(collision))
        assertEquals(SignInResult.WeakPassword, FirebaseAuthAdapter.mapCreateAccountException(weakPassword))
        assertEquals(SignInResult.InvalidEmail, FirebaseAuthAdapter.mapCreateAccountException(invalidEmail))
        assertEquals(SignInResult.Connectivity, FirebaseAuthAdapter.mapCreateAccountException(networkError))
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
    fun `successful createAccount returns AuthResult Success with bound user and token`() = runTest {
        val adapter = FakeAuthAdapter()

        val result = adapter.createAccount("newborrower@example.com", "strongPass123")
        assertTrue(result.isSuccess)
        assertTrue(result is AuthResult.Success)

        val success = result as AuthResult.Success
        val user = success.user
        assertNotNull(user.uid)
        assertEquals("newborrower@example.com", user.email)
        assertEquals(user.uid, adapter.currentUid)

        val tokenResult = user.getIdToken()
        assertTrue(tokenResult.isSuccess)
        assertEquals("token_abc_123", tokenResult.getOrNull())
    }

    @Test
    fun `failed createAccount returns mapped registration errors`() = runTest {
        val adapter = FakeAuthAdapter().apply {
            createAccountException = Exception("ERROR_EMAIL_ALREADY_IN_USE: Already exists")
        }

        val duplicateResult = adapter.createAccount("existing@example.com", "password123")
        assertTrue(duplicateResult is AuthResult.Failure)
        assertEquals(SignInResult.AccountExists, (duplicateResult as AuthResult.Failure).error)

        adapter.createAccountException = Exception("ERROR_WEAK_PASSWORD: Password should be at least 6 characters")
        val weakResult = adapter.createAccount("new@example.com", "123")
        assertTrue(weakResult is AuthResult.Failure)
        assertEquals(SignInResult.WeakPassword, (weakResult as AuthResult.Failure).error)

        adapter.createAccountException = Exception("ERROR_INVALID_EMAIL: Badly formatted email")
        val emailResult = adapter.createAccount("not-an-email", "password123")
        assertTrue(emailResult is AuthResult.Failure)
        assertEquals(SignInResult.InvalidEmail, (emailResult as AuthResult.Failure).error)
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
    fun `mutation lane serializes createAccount and prevents overlap on cancelled create task`() = runTest {
        val adapter = FakeAuthAdapter()
        val createTaskDeferred = CompletableDeferred<Unit>()
        adapter.sdkTaskDeferred = createTaskDeferred

        val createJob = launch {
            adapter.createAccount("creating@example.com", "passCreate")
        }

        testScheduler.advanceUntilIdle()

        // Cancel caller while SDK create task is still unsettled
        createJob.cancel()
        testScheduler.advanceUntilIdle()

        // Queue a subsequent signIn while the cancelled create task has not settled yet
        var signInStartedOrFinished = false
        val signInJob = launch {
            adapter.sdkTaskDeferred = null
            adapter.signIn("next@example.com", "passNext")
            signInStartedOrFinished = true
        }

        testScheduler.advanceUntilIdle()
        // signIn must remain blocked until the unsettled create task finishes and reconciles
        assertFalse(signInStartedOrFinished)
        assertFalse(createJob.isCompleted)

        // Now settle the underlying SDK create task
        createTaskDeferred.complete(Unit)
        testScheduler.advanceUntilIdle()

        createJob.join()
        signInJob.join()

        assertEquals(1, adapter.reconciledCancelledAttempts)
        assertTrue(signInStartedOrFinished)
        assertEquals("next@example.com", adapter.currentUserEmail)
    }

    @Test
    fun `cancelled createAccount reconciles and clears SDK user when no subsequent operation runs`() = runTest {
        val adapter = FakeAuthAdapter()
        val createTaskDeferred = CompletableDeferred<Unit>()
        adapter.sdkTaskDeferred = createTaskDeferred

        val createJob = launch {
            adapter.createAccount("orphan@example.com", "passOrphan")
        }

        testScheduler.advanceUntilIdle()
        createJob.cancel()
        testScheduler.advanceUntilIdle()

        createTaskDeferred.complete(Unit)
        testScheduler.advanceUntilIdle()
        createJob.join()

        assertEquals(1, adapter.reconciledCancelledAttempts)
        assertNull(adapter.currentUid)
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
