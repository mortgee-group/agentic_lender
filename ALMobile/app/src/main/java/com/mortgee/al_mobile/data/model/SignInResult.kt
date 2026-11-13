package com.mortgee.al_mobile.data.model

/**
 * Represents safe outcomes of an account sign-in attempt.
 *
 * All outcome types are kept free of credentials, tokens, and raw exceptions.
 */
sealed interface SignInResult {
    /** Successful sign-in and session establishment. */
    data object Success : SignInResult

    /** Sign-in attempt rejected because an operation is already in progress. */
    data object Busy : SignInResult

    /** Invalid email or password credentials. */
    data object InvalidCredentials : SignInResult

    /** The user account is disabled or suspended. */
    data object DisabledAccount : SignInResult

    /** Network connectivity or transport failure. */
    data object Connectivity : SignInResult

    /** Authentication rejected by the server (e.g. 401 response or invalid token). */
    data object RejectedAuthentication : SignInResult

    /** Account profile was not found on the server (e.g. 404 response). */
    data object MissingProfile : SignInResult

    /** User role is absent, invalid, or unsupported. */
    data object UnsupportedRole : SignInResult

    /** Generic or unexpected failure. */
    data object GenericFailure : SignInResult

    val isSuccess: Boolean
        get() = this is Success
}
