package com.mortgee.al_mobile.data.session

/**
 * Storage contract for reading and writing partitioned private user data.
 *
 * Enforces UID partition isolation for reads and guards private writes with the full [SessionKey].
 */
interface UserPrivateStore {
    /**
     * Reads a private record for [key] associated with the captured [uid].
     *
     * Ignores legacy unpartitioned records whose owner cannot be verified.
     *
     * @param uid The captured target user ID.
     * @param key The data key to read.
     * @return The stored string value if present and owned by [uid], or null otherwise.
     */
    suspend fun getString(uid: String, key: String): String?

    /**
     * Writes a private record for [key] with [value], guarded by [sessionKey].
     *
     * Checks [sessionKey] against the active [SessionOwner.currentKey]. If [sessionKey] is stale,
     * invalid, or does not match the active session, the write is rejected and returns false.
     *
     * @param sessionKey The full session key (uid and generation) capturing the active session at operation entry.
     * @param key The data key to write.
     * @param value The string value to store.
     * @return true if the write was authorized and succeeded, false if rejected or failed.
     */
    suspend fun putString(sessionKey: SessionKey, key: String, value: String): Boolean

    /**
     * Clears a private record for [key], guarded by [sessionKey].
     *
     * @param sessionKey The full session key capturing the active session.
     * @param key The data key to remove.
     * @return true if authorized and cleared, false otherwise.
     */
    suspend fun removeString(sessionKey: SessionKey, key: String): Boolean

    /**
     * Clears all private data belonging to [uid], guarded by [sessionKey].
     *
     * @param sessionKey The full session key.
     * @return true if authorized and cleared, false otherwise.
     */
    suspend fun clearUserPartition(sessionKey: SessionKey): Boolean

    /**
     * Returns true if a private record for [key] owned by [uid] exists.
     */
    suspend fun containsKey(uid: String, key: String): Boolean
}
