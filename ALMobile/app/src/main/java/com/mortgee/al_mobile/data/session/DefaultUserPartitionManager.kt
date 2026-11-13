package com.mortgee.al_mobile.data.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [UserPartitionManager].
 *
 * Manages the captured UID's partition before session commit. If the app has no private stores,
 * the verified UID supplies the in-memory namespace. Supports custom [PartitionIoInitializer]
 * for storage/database setup and handles I/O failure safely.
 */
@Singleton
class DefaultUserPartitionManager @Inject constructor(
    private val ioInitializer: PartitionIoInitializer
) : UserPartitionManager {

    /**
     * Secondary constructor for standard usage without an explicit I/O initializer.
     * Uses default in-memory partition preparation.
     */
    constructor() : this(PartitionIoInitializer { true })

    private val mutex = Mutex()
    private var activeUid: String? = null

    override val currentPartitionUid: String?
        get() = activeUid

    override suspend fun preparePartition(uid: String): Boolean {
        if (uid.isBlank()) return false

        return mutex.withLock {
            try {
                val ioOk = ioInitializer.initializePartition(uid)
                if (ioOk) {
                    activeUid = uid
                    true
                } else {
                    activeUid = null
                    false
                }
            } catch (_: Exception) {
                activeUid = null
                false
            }
        }
    }

    override fun isPartitionReady(uid: String): Boolean {
        if (uid.isBlank()) return false
        return activeUid == uid
    }

    override suspend fun clearPartition() {
        mutex.withLock {
            activeUid = null
        }
    }
}
