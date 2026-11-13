package com.mortgee.al_mobile.data.session

/**
 * Interface for performing I/O or storage/database initialization for a UID partition.
 */
fun interface PartitionIoInitializer {
    suspend fun initializePartition(uid: String): Boolean
}

/**
 * Prepares and manages user-specific data partitions prior to session commitment.
 *
 * Ensures that private user storage, databases, or in-memory namespaces associated
 * with [uid] are initialized and ready for authenticated operations.
 */
interface UserPartitionManager {
    /**
     * Current active in-memory partition UID, or null if no partition is ready.
     */
    val currentPartitionUid: String?

    /**
     * Prepares the partition for the specified candidate [uid].
     *
     * Must be called after UID and role validation, but before session commit.
     *
     * @param uid The verified user ID to partition data for.
     * @return true if partition preparation succeeded and is ready, false if preparation or I/O failed.
     */
    suspend fun preparePartition(uid: String): Boolean

    /**
     * Returns true if and only if the partition for [uid] is initialized and ready.
     */
    fun isPartitionReady(uid: String): Boolean

    /**
     * Clears current in-memory partition state on sign-out or session invalidation.
     */
    suspend fun clearPartition()
}
