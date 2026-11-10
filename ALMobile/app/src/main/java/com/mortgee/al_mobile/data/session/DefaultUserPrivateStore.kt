package com.mortgee.al_mobile.data.session

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [UserPrivateStore].
 *
 * Uses the captured UID for reads and guards private writes with the full [SessionKey].
 * Verifies that the provided [SessionKey] matches the active [SessionOwner.currentKey]
 * and that the partition for [SessionKey.uid] is ready via [UserPartitionManager].
 *
 * Legacy unpartitioned data (stored without a verified UID owner tag) is safely ignored.
 */
@Singleton
class DefaultUserPrivateStore @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val sessionOwner: SessionOwner,
    private val userPartitionManager: UserPartitionManager
) : UserPrivateStore {

    override suspend fun getString(uid: String, key: String): String? {
        if (uid.isBlank() || key.isBlank()) return null

        val pKey = partitionKey(uid, key)
        return try {
            if (sharedPreferences.contains(pKey)) {
                sharedPreferences.getString(pKey, null)
            } else {
                // Ignore legacy unpartitioned records whose owner cannot be verified.
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun putString(sessionKey: SessionKey, key: String, value: String): Boolean {
        if (key.isBlank() || sessionKey.uid.isBlank()) return false

        // Guard 1: Validate session key against current active session
        val current = sessionOwner.currentKey
        if (current == null || current != sessionKey) {
            return false
        }

        // Guard 2: Validate partition readiness
        if (!userPartitionManager.isPartitionReady(sessionKey.uid)) {
            return false
        }

        val pKey = partitionKey(sessionKey.uid, key)
        return try {
            sharedPreferences.edit()
                .putString(pKey, value)
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun removeString(sessionKey: SessionKey, key: String): Boolean {
        if (key.isBlank() || sessionKey.uid.isBlank()) return false

        val current = sessionOwner.currentKey
        if (current == null || current != sessionKey) {
            return false
        }

        if (!userPartitionManager.isPartitionReady(sessionKey.uid)) {
            return false
        }

        val pKey = partitionKey(sessionKey.uid, key)
        return try {
            sharedPreferences.edit()
                .remove(pKey)
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun clearUserPartition(sessionKey: SessionKey): Boolean {
        if (sessionKey.uid.isBlank()) return false

        val current = sessionOwner.currentKey
        if (current == null || current != sessionKey) {
            return false
        }

        if (!userPartitionManager.isPartitionReady(sessionKey.uid)) {
            return false
        }

        val prefix = partitionPrefix(sessionKey.uid)
        return try {
            val editor = sharedPreferences.edit()
            val allKeys = sharedPreferences.all.keys
            for (k in allKeys) {
                if (k.startsWith(prefix)) {
                    editor.remove(k)
                }
            }
            editor.commit()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun containsKey(uid: String, key: String): Boolean {
        if (uid.isBlank() || key.isBlank()) return false
        val pKey = partitionKey(uid, key)
        return sharedPreferences.contains(pKey)
    }

    companion object {
        fun partitionPrefix(uid: String): String = "user_partition_${uid}_"
        fun partitionKey(uid: String, key: String): String = "${partitionPrefix(uid)}$key"
    }
}
