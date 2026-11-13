package com.mortgee.al_mobile.data.session

/**
 * Storage contract for persisting the non-secret marker of a completed session.
 *
 * The app persists only the non-secret marker (verified UID) needed to distinguish
 * a completed session from an interrupted attempt.
 */
interface CompletedSessionStorage {
    /**
     * Durably stores the verified UID of an authenticated session.
     * @return true if the write succeeded, false otherwise.
     */
    suspend fun saveCompletedUid(uid: String): Boolean

    /**
     * Reads the durably stored completed UID marker.
     * @return the verified UID if present, null otherwise.
     */
    suspend fun getCompletedUid(): String?

    /**
     * Durably removes the completed UID marker.
     * @return true if removal succeeded, false otherwise.
     */
    suspend fun clearCompletedUid(): Boolean
}
