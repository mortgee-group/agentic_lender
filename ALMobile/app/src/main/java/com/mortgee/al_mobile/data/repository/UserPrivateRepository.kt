package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.network.PrivateTokenException
import com.mortgee.al_mobile.data.network.PrivateTokenSeam
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.UserPrivateStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repository interface for managing private user data operations.
 *
 * Enforces session key isolation by capturing the entry [SessionKey]
 * and carrying it through network requests, private store reads/writes,
 * and result publication.
 */
interface UserPrivateRepository {
    /**
     * Reads a partitioned private string record for [key], guarded by [entryKey].
     *
     * @param entryKey The caller's captured session key (or current session key if null).
     * @param key The data key to read.
     * @return [Result.success] with stored value or null if absent; or [Result.failure] if session is missing or stale.
     */
    suspend fun getPrivatePreference(entryKey: SessionKey? = null, key: String): Result<String?>

    /**
     * Writes a partitioned private string record for [key] with [value], guarded by [entryKey].
     *
     * @param entryKey The caller's captured session key (or current session key if null).
     * @param key The data key to write.
     * @param value The value to store.
     * @return [Result.success] with true if written; or [Result.failure] if session is missing, stale, or write rejected.
     */
    suspend fun setPrivatePreference(entryKey: SessionKey? = null, key: String, value: String): Result<Boolean>

    /**
     * Executes a private remote operation using [PrivateTokenSeam], guarded by [entryKey].
     *
     * @param entryKey The caller's captured session key (or current session key if null).
     * @param isMutation Whether the request is a mutation.
     * @param block The suspendable request block receiving the bearer token.
     * @return [Result.success] with result of block, or [Result.failure] if session is missing/stale or block failed.
     */
    suspend fun <T> fetchPrivateRemoteData(
        entryKey: SessionKey? = null,
        isMutation: Boolean = false,
        block: suspend (token: String) -> T
    ): Result<T>
}

/**
 * Concrete implementation of [UserPrivateRepository].
 *
 * Inventory finding for Task 03.11: Inventory confirmed no pre-existing private domain consumers
 * in the repository layer. This class acts as the private repository implementation that preserves
 * session ordering, carrying the original entry [SessionKey] through network requests via [PrivateTokenSeam]
 * and partitioned storage via [UserPrivateStore], and guarding result publication against stale sessions.
 */
@Singleton
class DefaultUserPrivateRepository @Inject constructor(
    private val sessionOwner: SessionOwner,
    private val userPrivateStore: UserPrivateStore,
    private val privateTokenSeam: PrivateTokenSeam
) : UserPrivateRepository {

    override suspend fun getPrivatePreference(entryKey: SessionKey?, key: String): Result<String?> {
        val capturedKey = entryKey ?: sessionOwner.currentKey
            ?: return Result.failure(PrivateTokenException.NoActiveSession())

        val currentBefore = sessionOwner.currentKey
        if (currentBefore != capturedKey) {
            return Result.failure(
                PrivateTokenException.StaleSession(capturedKey = capturedKey, currentKey = currentBefore)
            )
        }

        val value = try {
            userPrivateStore.getString(capturedKey.uid, key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(e)
        }

        val currentAfter = sessionOwner.currentKey
        if (currentAfter != capturedKey) {
            return Result.failure(
                PrivateTokenException.StaleSession(capturedKey = capturedKey, currentKey = currentAfter)
            )
        }

        return Result.success(value)
    }

    override suspend fun setPrivatePreference(entryKey: SessionKey?, key: String, value: String): Result<Boolean> {
        val capturedKey = entryKey ?: sessionOwner.currentKey
            ?: return Result.failure(PrivateTokenException.NoActiveSession())

        val currentBefore = sessionOwner.currentKey
        if (currentBefore != capturedKey) {
            return Result.failure(
                PrivateTokenException.StaleSession(capturedKey = capturedKey, currentKey = currentBefore)
            )
        }

        val success = try {
            userPrivateStore.putString(capturedKey, key, value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(e)
        }

        val currentAfter = sessionOwner.currentKey
        if (currentAfter != capturedKey) {
            return Result.failure(
                PrivateTokenException.StaleSession(capturedKey = capturedKey, currentKey = currentAfter)
            )
        }

        if (!success) {
            return Result.failure(Exception("Write rejected for session key $capturedKey"))
        }

        return Result.success(true)
    }

    override suspend fun <T> fetchPrivateRemoteData(
        entryKey: SessionKey?,
        isMutation: Boolean,
        block: suspend (token: String) -> T
    ): Result<T> {
        val capturedKey = entryKey ?: sessionOwner.currentKey
            ?: return Result.failure(PrivateTokenException.NoActiveSession())

        val result = privateTokenSeam.executePrivate(capturedKey, isMutation, block)

        val currentAfter = sessionOwner.currentKey
        if (currentAfter != capturedKey) {
            return Result.failure(
                PrivateTokenException.StaleSession(capturedKey = capturedKey, currentKey = currentAfter)
            )
        }

        return result
    }
}
