package com.mortgee.al_mobile.data.session

/**
 * Unique, immutable process-local identifier for an established session.
 *
 * A published session has a distinct process-local key `(uid, generation)`.
 * A fresh key is generated on each successful establishment, including same-UID sign-in and restore.
 *
 * @property uid The backend/Firebase user identifier.
 * @property generation A process-local monotonically increasing generation number,
 *                      ensuring distinct session keys across re-authentications.
 */
data class SessionKey(
    val uid: String,
    val generation: Long,
)
