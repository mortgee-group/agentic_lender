package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.auth.AuthResult
import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.network.ProfileApiService
import com.mortgee.al_mobile.data.network.model.CreateProfileRequest
import com.mortgee.al_mobile.data.network.model.UserProfileDto
import com.mortgee.al_mobile.data.session.CompletedSessionStorage
import com.mortgee.al_mobile.data.session.SessionAttempt
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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
            }

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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
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

    override suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phoneNumber: String?,
    ): SignInResult {
        validateProfileFields(firstName, lastName, phoneNumber)?.let { fieldError ->
            return fieldError
        }

        val attempt = sessionOwner.startSignInAttempt() ?: return SignInResult.Busy

        return try {
            val authResult = authAdapter.createAccount(email, password)
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

            return createAndCommitBorrowerProfile(
                attempt = attempt,
                candidateUid = candidateUser.uid,
                bearerHeader = "Bearer $token",
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
            )
        } catch (e: CancellationException) {
            sessionOwner.cancelAttempt(attempt)
            throw e
        } catch (_: Exception) {
            sessionOwner.cancelAttempt(attempt)
            SignInResult.GenericFailure
        }
    }

    override suspend fun completeRegistration(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phoneNumber: String?,
    ): SignInResult {
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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
            }

            val bearerHeader = "Bearer $token"
            val existingProfile = try {
                profileApiService.getProfile(bearerHeader)
            } catch (e: HttpException) {
                if (e.code() == 404 && isConfirmedMissingProfile404(e)) {
                    null
                } else {
                    sessionOwner.cancelAttempt(attempt)
                    return when (e.code()) {
                        400, 401, 403 -> SignInResult.RejectedAuthentication
                        else -> SignInResult.GenericFailure
                    }
                }
            } catch (_: IOException) {
                sessionOwner.cancelAttempt(attempt)
                return SignInResult.Connectivity
            }

            if (existingProfile != null) {
                return verifyBorrowerAndCommitSession(
                    attempt = attempt,
                    candidateUid = candidateUser.uid,
                    profile = existingProfile,
                )
            }

            val localFieldError = validateProfileFields(firstName, lastName, phoneNumber)
            if (localFieldError != null) {
                sessionOwner.cancelAttempt(attempt)
                return localFieldError
            }

            return createAndCommitBorrowerProfile(
                attempt = attempt,
                candidateUid = candidateUser.uid,
                bearerHeader = bearerHeader,
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
            )
        } catch (e: CancellationException) {
            sessionOwner.cancelAttempt(attempt)
            throw e
        } catch (_: Exception) {
            sessionOwner.cancelAttempt(attempt)
            SignInResult.GenericFailure
        }
    }

    private suspend fun createAndCommitBorrowerProfile(
        attempt: SessionAttempt,
        candidateUid: String,
        bearerHeader: String,
        firstName: String,
        lastName: String,
        phoneNumber: String?,
    ): SignInResult {
        if (!sessionOwner.isAttemptCurrent(attempt)) {
            return SignInResult.GenericFailure
        }

        val profile = try {
            profileApiService.createProfile(
                bearerToken = bearerHeader,
                request = CreateProfileRequest.forBorrower(
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = phoneNumber,
                ),
            )
        } catch (e: HttpException) {
            if (e.code() == 409) {
                reconcileProfileAfterAmbiguousCreate(
                    attempt = attempt,
                    candidateUid = candidateUid,
                    bearerHeader = bearerHeader,
                ) ?: return failAmbiguousProfileReconciliation(attempt)
            } else {
                sessionOwner.cancelAttempt(attempt)
                return mapProfileCreateHttpException(e)
            }
        } catch (_: IOException) {
            reconcileProfileAfterAmbiguousCreate(
                attempt = attempt,
                candidateUid = candidateUid,
                bearerHeader = bearerHeader,
            ) ?: return failAmbiguousProfileReconciliation(attempt)
        }

        return verifyBorrowerAndCommitSession(
            attempt = attempt,
            candidateUid = candidateUid,
            profile = profile,
        )
    }

    private suspend fun verifyBorrowerAndCommitSession(
        attempt: SessionAttempt,
        candidateUid: String,
        profile: UserProfileDto,
    ): SignInResult {
        if (!sessionOwner.isAttemptCurrent(attempt)) {
            return SignInResult.GenericFailure
        }

        if (profile.uid != candidateUid) {
            sessionOwner.cancelAttempt(attempt)
            return SignInResult.RejectedAuthentication
        }

        val role = profile.decodeRole()
        if (role != BackendRole.BORROWER) {
            sessionOwner.cancelAttempt(attempt)
            return SignInResult.UnsupportedRole
        }

        if (!sessionOwner.isAttemptCurrent(attempt)) {
            return SignInResult.GenericFailure
        }

        val partitionReady = userPartitionManager.preparePartition(candidateUid)
        if (!partitionReady) {
            sessionOwner.cancelAttempt(attempt)
            return SignInResult.GenericFailure
        }

        val committed = sessionOwner.commitSession(
            attempt = attempt,
            uid = candidateUid,
            email = profile.email,
            role = role,
            displayName = profile.displayName,
            firstName = profile.firstName,
            lastName = profile.lastName,
            phoneNumber = profile.phoneNumber,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
        )

        return if (committed) {
            SignInResult.Success
        } else {
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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
            }

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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
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

            if (!sessionOwner.isAttemptCurrent(attempt)) {
                return SignInResult.GenericFailure
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

    private suspend fun reconcileProfileAfterAmbiguousCreate(
        attempt: SessionAttempt,
        candidateUid: String,
        bearerHeader: String,
    ): UserProfileDto? {
        if (!sessionOwner.isAttemptCurrent(attempt) || authAdapter.currentUid != candidateUid) {
            return null
        }

        return try {
            profileApiService.getProfile(bearerHeader)
        } catch (e: CancellationException) {
            throw e
        } catch (_: HttpException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun failAmbiguousProfileReconciliation(attempt: SessionAttempt): SignInResult {
        val wasCurrent = sessionOwner.isAttemptCurrent(attempt)
        sessionOwner.cancelAttempt(attempt)
        return if (wasCurrent) {
            SignInResult.CompletionNeeded
        } else {
            SignInResult.GenericFailure
        }
    }

    private fun validateProfileFields(
        firstName: String,
        lastName: String,
        phoneNumber: String?,
    ): SignInResult? {
        if (firstName.isBlank() || firstName.length > MAX_FIRST_NAME_LENGTH) {
            return SignInResult.InvalidFirstName
        }
        if (lastName.isBlank() || lastName.length > MAX_LAST_NAME_LENGTH) {
            return SignInResult.InvalidLastName
        }
        if (phoneNumber != null && phoneNumber.isNotBlank() && phoneNumber.length > MAX_PHONE_NUMBER_LENGTH) {
            return SignInResult.InvalidPhoneNumber
        }
        return null
    }

    private fun isConfirmedMissingProfile404(e: HttpException): Boolean {
        val response = e.response()
        val requestPath = response?.raw()?.request?.url?.encodedPath
        if (requestPath != null && requestPath != "/" && !requestPath.endsWith("/users/me")) {
            return false
        }
        val errorBody = try {
            response?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (errorBody.isNullOrBlank()) return false
        val trimmed = errorBody.trim()
        return trimmed.startsWith("{") && trimmed.endsWith("}") &&
            MISSING_PROFILE_404_REGEX.containsMatchIn(trimmed)
    }

    private fun mapProfileCreateHttpException(e: HttpException): SignInResult {
        return when (e.code()) {
            400, 401, 403 -> SignInResult.RejectedAuthentication
            409 -> SignInResult.CompletionNeeded
            422 -> {
                val errorBody = try {
                    e.response()?.errorBody()?.string()
                } catch (_: Exception) {
                    null
                }
                mapProfileCreate422Error(errorBody)
            }
            else -> SignInResult.CompletionNeeded
        }
    }

    private fun mapProfileCreate422Error(errorBody: String?): SignInResult {
        if (errorBody.isNullOrBlank()) return SignInResult.GenericFailure
        val trimmed = errorBody.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return SignInResult.GenericFailure
        }
        return when {
            FIRST_NAME_LOC_REGEX.containsMatchIn(trimmed) -> SignInResult.InvalidFirstName
            LAST_NAME_LOC_REGEX.containsMatchIn(trimmed) -> SignInResult.InvalidLastName
            PHONE_NUMBER_LOC_REGEX.containsMatchIn(trimmed) -> SignInResult.InvalidPhoneNumber
            DISPLAY_NAME_FIELD_REGEX.containsMatchIn(trimmed) -> SignInResult.InvalidDisplayName
            else -> SignInResult.GenericFailure
        }
    }

    private fun isNetworkException(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is IOException) return true
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("network") || msg.contains("connection") || msg.contains("unreachable")
    }

    companion object {
        const val MAX_FIRST_NAME_LENGTH = 30
        const val MAX_LAST_NAME_LENGTH = 50
        const val MAX_PHONE_NUMBER_LENGTH = 20

        private val FIRST_NAME_LOC_REGEX = Regex(
            """"loc"\s*:\s*\[\s*"body"\s*,\s*"first_name"\s*]|"(first_name|firstName)""""
        )
        private val LAST_NAME_LOC_REGEX = Regex(
            """"loc"\s*:\s*\[\s*"body"\s*,\s*"last_name"\s*]|"(last_name|lastName)""""
        )
        private val PHONE_NUMBER_LOC_REGEX = Regex(
            """"loc"\s*:\s*\[\s*"body"\s*,\s*"phone_number"\s*]|"(phone_number|phoneNumber)""""
        )
        private val DISPLAY_NAME_FIELD_REGEX = Regex("\"(display_name|displayName)\"")
        private val MISSING_PROFILE_404_REGEX = Regex(
            """"(detail|error)"\s*:\s*"[^"]*(User profile not found|Profile not found|profilestillmissing|missing)[^"]*"""",
            RegexOption.IGNORE_CASE,
        )
    }
}
