package com.mortgee.al_mobile.data.session

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.model.BackendRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [SessionOwner].
 *
 * Serves as the sole authority for identity-changing operations, publication of session state,
 * guarded result publication, invalidation, and restoration.
 *
 * Uses a [Mutex] gate to serialize state transitions and checks, ensuring atomic commits,
 * immediate attempt retirement, and proper isolation of completed sessions across generations.
 */
@Singleton
class DefaultSessionOwner @Inject constructor(
    private val storage: CompletedSessionStorage,
    private val authAdapter: AuthAdapter
) : SessionOwner {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val gateMutex = Mutex()
    private var currentGeneration = 0L
    private var activeAttempt: SessionAttempt? = null

    override suspend fun startSignInAttempt(): SessionAttempt? {
        return gateMutex.withLock {
            if (_sessionState.value !is SessionState.SignedOut) return@withLock null
            val attempt = SessionAttempt(UUID.randomUUID().toString(), SessionAttempt.Type.SIGN_IN)
            activeAttempt = attempt
            _sessionState.value = SessionState.Establishing(attempt.attemptId)
            attempt
        }
    }

    override suspend fun startRestoreAttempt(): SessionAttempt? {
        return gateMutex.withLock {
            if (_sessionState.value !is SessionState.SignedOut) return@withLock null
            val attempt = SessionAttempt(UUID.randomUUID().toString(), SessionAttempt.Type.RESTORE)
            activeAttempt = attempt
            _sessionState.value = SessionState.Restoring(attempt.attemptId)
            attempt
        }
    }

    override suspend fun commitSession(
        attempt: SessionAttempt,
        uid: String,
        email: String,
        role: BackendRole,
        displayName: String?
    ): Boolean {
        val (isMatch, saveSuccess) = gateMutex.withLock {
            if (activeAttempt != attempt) return@withLock Pair(false, false)
            if (uid.isBlank() || email.isBlank()) return@withLock Pair(true, false)

            val saveOk = storage.saveCompletedUid(uid)
            if (!saveOk) {
                _sessionState.value = SessionState.SignedOut
                activeAttempt = null
                return@withLock Pair(true, false)
            }

            currentGeneration++
            val sessionKey = SessionKey(uid, currentGeneration)
            _sessionState.value = SessionState.Authenticated(
                key = sessionKey,
                uid = uid,
                email = email,
                role = role,
                displayName = displayName
            )
            activeAttempt = null
            Pair(true, true)
        }

        if (isMatch && !saveSuccess) {
            authAdapter.signOut()
            return false
        }

        return isMatch && saveSuccess
    }

    override suspend fun invalidateSession(): Boolean {
        val cleared = gateMutex.withLock {
            activeAttempt = null
            _sessionState.value = SessionState.SignedOut
            storage.clearCompletedUid()
        }
        val sdkSignedOut = authAdapter.signOut()
        return cleared && sdkSignedOut
    }

    override suspend fun signOut(): Boolean {
        return invalidateSession()
    }

    override suspend fun cancelAttempt(attempt: SessionAttempt) {
        val matches = gateMutex.withLock {
            if (activeAttempt == attempt) {
                activeAttempt = null
                _sessionState.value = SessionState.SignedOut
                true
            } else {
                false
            }
        }
        if (matches) {
            authAdapter.signOut()
        }
    }
}
