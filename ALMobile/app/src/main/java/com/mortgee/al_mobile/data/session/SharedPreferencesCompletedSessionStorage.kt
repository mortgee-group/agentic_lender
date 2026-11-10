package com.mortgee.al_mobile.data.session

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [SharedPreferences] implementation of [CompletedSessionStorage].
 *
 * Durably persists and clears the non-secret verified UID marker of a completed session.
 * Uses [SharedPreferences.Editor.commit] to enforce synchronous, durable persistence
 * to disk, ensuring write and removal failures are observable.
 */
@Singleton
class SharedPreferencesCompletedSessionStorage @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : CompletedSessionStorage {

    override suspend fun saveCompletedUid(uid: String): Boolean {
        if (uid.isBlank()) return false
        return try {
            sharedPreferences.edit()
                .putString(KEY_COMPLETED_UID, uid)
                .commit()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getCompletedUid(): String? {
        return try {
            val uid = sharedPreferences.getString(KEY_COMPLETED_UID, null)
            if (uid.isNullOrBlank()) null else uid
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun clearCompletedUid(): Boolean {
        return try {
            sharedPreferences.edit()
                .remove(KEY_COMPLETED_UID)
                .commit()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val KEY_COMPLETED_UID = "completed_session_uid"

        /**
         * Computes the preference storage name for the environment namespace derived from the API base URL.
         */
        fun getStorageName(apiBaseUrl: String): String {
            val sanitized = apiBaseUrl.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
            return "al_mobile_session_$sanitized"
        }
    }
}
