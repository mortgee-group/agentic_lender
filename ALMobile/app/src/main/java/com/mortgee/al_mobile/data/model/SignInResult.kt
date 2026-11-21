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

    /**
     * Email address is malformed or rejected by Firebase (`ERROR_INVALID_EMAIL`)
     * or local validation. Targets the email input control.
     */
    data object InvalidEmail : SignInResult

    /**
     * Password does not meet strength requirements (`FirebaseAuthWeakPasswordException` / `ERROR_WEAK_PASSWORD`)
     * or local validation. Targets the password input control.
     */
    data object WeakPassword : SignInResult

    /**
     * First name is blank, exceeds length limits (1–30), or is rejected by AL-Core
     * (`HTTP 422` with verified `body.first_name` field error). Targets the first name input control.
     */
    data object InvalidFirstName : SignInResult

    /**
     * Last name is blank, exceeds length limits (1–50), or is rejected by AL-Core
     * (`HTTP 422` with verified `body.last_name` field error). Targets the last name input control.
     */
    data object InvalidLastName : SignInResult

    /**
     * Optional phone number exceeds length limits (max 20) or is rejected by AL-Core
     * (`HTTP 422` with verified `body.phone_number` field error). Targets the phone number input control.
     */
    data object InvalidPhoneNumber : SignInResult

    /**
     * Display name is blank locally or rejected by AL-Core (`HTTP 422` with verified `display_name` field error).
     * Targets the display name input control.
     */
    data object InvalidDisplayName : SignInResult

    /**
     * Firebase reports that an account with this email already exists
     * (`FirebaseAuthUserCollisionException` / `ERROR_EMAIL_ALREADY_IN_USE`).
     * Offers ordinary Sign-In and explicit "Finish registration" without silently signing in.
     */
    data object AccountExists : SignInResult

    /**
     * The Firebase credential account was created, but AL-Core profile establishment could not be
     * settled. Candidate session is closed via Task 03 cleanup and the user is offered
     * explicit "Finish registration".
     */
    data object CompletionNeeded : SignInResult

    val isSuccess: Boolean
        get() = this is Success
}
