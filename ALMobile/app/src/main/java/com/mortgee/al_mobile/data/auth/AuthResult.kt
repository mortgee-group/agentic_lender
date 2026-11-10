package com.mortgee.al_mobile.data.auth

import com.mortgee.al_mobile.data.model.SignInResult

/**
 * Outcome of an SDK sign-in attempt via [AuthAdapter].
 */
sealed interface AuthResult {
    /**
     * Successful SDK sign-in yielding a candidate [AuthUser] bound to the sign-in result.
     */
    data class Success(val user: AuthUser) : AuthResult

    /**
     * Failed SDK sign-in mapped to a safe [SignInResult] error outcome.
     */
    data class Failure(val error: SignInResult) : AuthResult

    val isSuccess: Boolean
        get() = this is Success
}
