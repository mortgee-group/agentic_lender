package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.auth.AuthResult
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.network.ProfileApiService
import com.mortgee.al_mobile.data.session.CompletedSessionStorage
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.UserPartitionManager
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [AccountRepository].
 *
 * Coordinates candidate authentication via [AuthAdapter], profile validation via [ProfileApiService],
 * user partition initialization via [UserPartitionManager], and session commitment through [SessionOwner].
 */
@Singleton
class ApiAccountRepository @Inject constructor(
    private val authAdapter: AuthAdapter,
    private val profileApiService: ProfileApiService,
    private val sessionOwner: SessionOwner,
    private val userPartitionManager: UserPartitionManager,
    private val completedSessionStorage: CompletedSessionStorage,
) : AccountRepository {

    override suspend fun signIn(email: String, password: String): SignInResult {
        val attempt = sessionOwner.startSignInAttempt() ?: return SignInResult.Busy

        return try {
            val authResult = authAdapter.signIn(email, password)
            if (authResult is AuthResult.Failure) {
                sessionOwner.cancelAttempt(attempt)
                return authResult.error
            }
            val candidateUser = (authResult as AuthResult.Success).user

            val tokenResult = candidateUser.getIdToken()
            if (tokenResult.isFailure) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.RejectedAuthentication
            }
            val token = tokenResult.getOrThrow()

            val profile = try {
                profileApiService.getProfile("Bearer $token")
            } catch (e: HttpException) {
                sessionOwner.cancelAttempt(attempt)
                return when (e.code()) {
                    401, 403 -> SignInResult.RejectedAuthentication
                    404 -> SignInResult.MissingProfile
                    else -> SignInResult.GenericFailure
                }
            } catch (_: IOException) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.Connectivity
            }

            if (profile.uid != candidateUser.uid) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.RejectedAuthentication
            }

            val role = profile.decodeRole()
            if (role == null) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.UnsupportedRole
            }

            val partitionReady = userPartitionManager.preparePartition(candidateUser.uid)
            if (!partitionReady) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.GenericFailure
            }

            val committed = sessionOwner.commitSession(
                attempt = attempt,
                uid = candidateUser.uid,
                email = profile.email,
                role = role,
                displayName = profile.displayName,
                firstName = profile.firstName,
                lastName = profile.lastName,
                phoneNumber = profile.phoneNumber,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
            )

            if (committed) {
                SignInResult.Success
            } else {
                SignInResult.GenericFailure
            }
        } catch (e: CancellationException) {
            sessionOwner.cancelAttempt(attempt)
            throw e
        } catch (_: Exception) {
            sessionOwner.cancelAttempt(attempt)
            SignInResult.GenericFailure
        }
    }

    override suspend fun restoreSession(): SignInResult {
        val attempt = sessionOwner.startRestoreAttempt() ?: return SignInResult.Busy

        val completedUid = completedSessionStorage.getCompletedUid()
        if (completedUid.isNullOrBlank()) {
            sessionOwner.cancelAttempt(attempt)
            return SignInResult.GenericFailure
        }

        return try {
            val currentSdkUid = authAdapter.currentUid
            if (currentSdkUid.isNullOrBlank() || currentSdkUid != completedUid) {
                sessionOwner.invalidateSession()
                return SignInResult.RejectedAuthentication
            }

            val tokenResult = authAdapter.getToken(completedUid, forceRefresh = false)
            if (tokenResult.isFailure) {
                val exception = tokenResult.exceptionOrNull()
                if (exception is IOException || isNetworkException(exception)) {
                    sessionOwner.cancelAttempt(attempt)
                    return SignInResult.Connectivity
                } else {
                    sessionOwner.invalidateSession()
                    return SignInResult.RejectedAuthentication
                }
            }
            val token = tokenResult.getOrThrow()

            val profile = try {
                profileApiService.getProfile("Bearer $token")
            } catch (e: HttpException) {
                sessionOwner.invalidateSession()
                return when (e.code()) {
                    401, 403 -> SignInResult.RejectedAuthentication
                    404 -> SignInResult.MissingProfile
                    else -> SignInResult.GenericFailure
                }
            } catch (_: IOException) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.Connectivity
            }

            if (profile.uid != completedUid) {
                sessionOwner.invalidateSession()
                return SignInResult.RejectedAuthentication
            }

            val role = profile.decodeRole()
            if (role == null) {
                sessionOwner.invalidateSession()
                return SignInResult.UnsupportedRole
            }

            val partitionReady = userPartitionManager.preparePartition(completedUid)
            if (!partitionReady) {
                sessionOwner.invalidateSession()
                return SignInResult.GenericFailure
            }

            val committed = sessionOwner.commitSession(
                attempt = attempt,
                uid = completedUid,
                email = profile.email,
                role = role,
                displayName = profile.displayName,
                firstName = profile.firstName,
                lastName = profile.lastName,
                phoneNumber = profile.phoneNumber,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
            )

            if (committed) {
                SignInResult.Success
            } else {
                sessionOwner.invalidateSession()
                SignInResult.GenericFailure
            }
        } catch (e: CancellationException) {
            sessionOwner.cancelAttempt(attempt)
            throw e
        } catch (_: Exception) {
            sessionOwner.invalidateSession()
            SignInResult.GenericFailure
        }
    }

    private fun isNetworkException(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is IOException) return true
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("network") || msg.contains("connection") || msg.contains("unreachable")
    }
}
