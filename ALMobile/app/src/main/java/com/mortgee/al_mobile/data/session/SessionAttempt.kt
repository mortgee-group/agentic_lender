package com.mortgee.al_mobile.data.session

/**
 * Represents ownership of an active sign-in or restoration attempt.
 *
 * [attemptId] uniquely identifies pending sign-in or restore work.
 * An attempt can be committed or cancelled through [SessionOwner].
 */
data class SessionAttempt(
    val attemptId: String,
    val type: Type,
) {
    enum class Type {
        SIGN_IN,
        RESTORE,
    }
}
