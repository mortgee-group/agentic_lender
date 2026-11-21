package com.mortgee.al_mobile.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult as FirebaseSdkAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.Task
import com.mortgee.al_mobile.data.model.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Implementation of [AuthAdapter] wrapping Firebase Authentication SDK operations.
 *
 * Manages the identity mutation lane, binds candidate UID and token lookup to the returned
 * [FirebaseUser], maps SDK exceptions to safe [SignInResult] errors, and retains terminal
 * completion of in-flight SDK Tasks even if the calling coroutine is cancelled.
 */
class FirebaseAuthAdapter(
    private val firebaseAuth: FirebaseAuth
) : AuthAdapter {

    override val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val mutationMutex = Mutex()

    override suspend fun signIn(email: String, password: String): AuthResult {
        return executeAuthTask(forCreate = false) {
            firebaseAuth.signInWithEmailAndPassword(email, password)
        }
    }

    override suspend fun createAccount(email: String, password: String): AuthResult {
        return executeAuthTask(forCreate = true) {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
        }
    }

    private suspend fun executeAuthTask(
        forCreate: Boolean,
        taskBlock: () -> Task<FirebaseSdkAuthResult>
    ): AuthResult {
        return mutationMutex.withLock {
            val task = taskBlock()
            var sdkUser: FirebaseUser? = null
            var sdkException: Exception? = null

            try {
                val authResult = task.await()
                sdkUser = authResult.user
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // The waiter was cancelled while the Firebase Task was in flight.
                    // Retain terminal completion independent of the cancelled waiter.
                    withContext(NonCancellable) {
                        runCatching { task.await() }
                        reconcileCancelledAttempt()
                    }
                    throw e
                }
                sdkException = e
            }

            if (sdkException != null) {
                val mappedError = if (forCreate) {
                    mapCreateAccountException(sdkException)
                } else {
                    mapFirebaseException(sdkException)
                }
                return@withLock AuthResult.Failure(mappedError)
            }

            val user = sdkUser
            if (user == null || user.uid.isBlank()) {
                return@withLock AuthResult.Failure(SignInResult.GenericFailure)
            }

            AuthResult.Success(FirebaseAuthUser(user))
        }
    }

    override suspend fun getToken(uid: String, forceRefresh: Boolean): Result<String> {
        return try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null || currentUser.uid != uid) {
                return Result.failure(IllegalStateException("Current SDK user missing or UID mismatch"))
            }
            val tokenResult = currentUser.getIdToken(forceRefresh).await()
            val token = tokenResult.token
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Token is null or blank"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Boolean {
        return mutationMutex.withLock {
            try {
                firebaseAuth.signOut()
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        }
    }

    private suspend fun reconcileCancelledAttempt() {
        try {
            if (firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
            }
        } catch (_: Exception) {
            // Ignore cleanup errors during reconciliation
        }
    }

    companion object {
        fun mapFirebaseException(e: Exception): SignInResult {
            if (e is FirebaseAuthWeakPasswordException) {
                return SignInResult.WeakPassword
            }
            if (e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException) {
                return SignInResult.InvalidCredentials
            }
            if (e is FirebaseAuthUserCollisionException) {
                return SignInResult.DisabledAccount
            }
            if (e is FirebaseNetworkException) {
                return SignInResult.Connectivity
            }
            if (e is FirebaseAuthException) {
                val errorCode = e.errorCode.uppercase()
                if (errorCode.contains("USER_DISABLED")) {
                    return SignInResult.DisabledAccount
                }
                if (errorCode.contains("WEAK_PASSWORD")) {
                    return SignInResult.WeakPassword
                }
                if (errorCode.contains("EMAIL_ALREADY_IN_USE")) {
                    return SignInResult.AccountExists
                }
                if (errorCode.contains("USER_NOT_FOUND") ||
                    errorCode.contains("WRONG_PASSWORD") ||
                    errorCode.contains("INVALID_EMAIL") ||
                    errorCode.contains("INVALID_CREDENTIAL")
                ) {
                    return SignInResult.InvalidCredentials
                }
                if (errorCode.contains("NETWORK") || errorCode.contains("CONNECTION")) {
                    return SignInResult.Connectivity
                }
            }
            val message = e.message?.lowercase() ?: ""
            return when {
                message.contains("network") || message.contains("connection") || message.contains("unreachable") ->
                    SignInResult.Connectivity
                message.contains("disabled") || message.contains("user_disabled") ->
                    SignInResult.DisabledAccount
                message.contains("weak_password") || message.contains("weak password") ->
                    SignInResult.WeakPassword
                message.contains("email_already_in_use") || message.contains("already in use") ->
                    SignInResult.AccountExists
                message.contains("invalid credential") || message.contains("wrong password") || message.contains("user not found") ->
                    SignInResult.InvalidCredentials
                else ->
                    SignInResult.GenericFailure
            }
        }

        fun mapCreateAccountException(e: Exception): SignInResult {
            if (e is FirebaseAuthWeakPasswordException) {
                return SignInResult.WeakPassword
            }
            if (e is FirebaseAuthUserCollisionException) {
                return SignInResult.AccountExists
            }
            if (e is FirebaseAuthInvalidCredentialsException) {
                val errorCode = e.errorCode.uppercase()
                return if (errorCode.contains("WEAK_PASSWORD")) {
                    SignInResult.WeakPassword
                } else {
                    SignInResult.InvalidEmail
                }
            }
            if (e is FirebaseNetworkException) {
                return SignInResult.Connectivity
            }
            if (e is FirebaseAuthException) {
                val errorCode = e.errorCode.uppercase()
                if (errorCode.contains("EMAIL_ALREADY_IN_USE") || errorCode.contains("COLLISION")) {
                    return SignInResult.AccountExists
                }
                if (errorCode.contains("WEAK_PASSWORD")) {
                    return SignInResult.WeakPassword
                }
                if (errorCode.contains("INVALID_EMAIL")) {
                    return SignInResult.InvalidEmail
                }
                if (errorCode.contains("USER_DISABLED")) {
                    return SignInResult.DisabledAccount
                }
                if (errorCode.contains("NETWORK") || errorCode.contains("CONNECTION")) {
                    return SignInResult.Connectivity
                }
            }
            val message = e.message?.lowercase() ?: ""
            return when {
                message.contains("email_already_in_use") || message.contains("already in use") || message.contains("collision") ->
                    SignInResult.AccountExists
                message.contains("weak_password") || message.contains("weak password") ->
                    SignInResult.WeakPassword
                message.contains("invalid_email") || message.contains("invalid email") || message.contains("badly formatted") ->
                    SignInResult.InvalidEmail
                else ->
                    mapFirebaseException(e)
            }
        }
    }
}

/**
 * Implementation of [AuthUser] wrapping a concrete [FirebaseUser] instance.
 */
class FirebaseAuthUser(
    private val firebaseUser: FirebaseUser
) : AuthUser {

    override val uid: String
        get() = firebaseUser.uid

    override val email: String?
        get() = firebaseUser.email

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> {
        return try {
            val tokenResult = firebaseUser.getIdToken(forceRefresh).await()
            val token = tokenResult.token
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Retrieved token is null or blank"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
