package com.mortgee.al_mobile.ui.register

import com.mortgee.al_mobile.data.model.SignInResult

/**
 * Submission mode for the Borrower registration form.
 */
enum class RegisterMode {
    /**
     * Standard Borrower registration: creates a new Firebase credential account
     * and establishes its AL-Core Borrower profile.
     */
    CreateAccount,

    /**
     * Explicit completion mode ("Finish registration"): authenticates an existing
     * Firebase credential account and reconciles or creates its missing AL-Core Borrower profile.
     */
    CompleteExisting,
}

/**
 * In-memory state holder for the Borrower registration form.
 *
 * Exposes [displayName], [email], transient [password], field/form error outcomes, and [mode].
 * Preserves [displayName], [email], and [mode] in memory across configuration changes and after
 * recoverable failures while clearing [password] when a submission attempt ends.
 * Defaults to an empty form on process recreation.
 *
 * Overrides [toString] to ensure sensitive credentials ([password]) are masked in state dumps,
 * logs, and diagnostic outputs.
 */
data class RegisterFormState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val mode: RegisterMode = RegisterMode.CreateAccount,
    val isSubmitting: Boolean = false,
    val error: SignInResult? = null,
    val displayNameError: SignInResult? = null,
    val emailError: SignInResult? = null,
    val passwordError: SignInResult? = null,
) {
    /**
     * True if [displayName] is non-blank.
     */
    val isDisplayNameValid: Boolean
        get() = displayName.isNotBlank()

    /**
     * True if [email] is non-blank.
     */
    val isEmailValid: Boolean
        get() = email.isNotBlank()

    /**
     * True if [password] is non-blank.
     */
    val isPasswordValid: Boolean
        get() = password.isNotBlank()

    /**
     * True if all required inputs ([displayName], [email], and [password]) are non-blank.
     */
    val inputIsValid: Boolean
        get() = isDisplayNameValid && isEmailValid && isPasswordValid

    /**
     * True if the form can be submitted (all inputs are valid and no submission is in progress).
     */
    val canSubmit: Boolean
        get() = inputIsValid && !isSubmitting

    /**
     * True when the form is currently in explicit completion mode ([RegisterMode.CompleteExisting]).
     */
    val isCompletionMode: Boolean
        get() = mode == RegisterMode.CompleteExisting

    /**
     * True when explicit registration completion ("Finish registration") is active or offered
     * following a duplicate-account collision or an unsettled profile creation attempt.
     */
    val canOfferCompletion: Boolean
        get() = isCompletionMode ||
            (error == SignInResult.AccountExists) ||
            (error == SignInResult.CompletionNeeded)

    /**
     * Non-field (form-level or account-level) error outcome, or null if the current error
     * is already targeted to a specific input field.
     */
    val formError: SignInResult?
        get() = error?.takeUnless {
            it == SignInResult.InvalidDisplayName ||
                it == SignInResult.InvalidEmail ||
                it == SignInResult.WeakPassword
        }

    /**
     * Returns a copy with [displayName] updated and any previous [displayNameError] cleared.
     */
    fun withDisplayName(newDisplayName: String): RegisterFormState = copy(
        displayName = newDisplayName,
        displayNameError = null,
        error = error?.takeUnless { it == SignInResult.InvalidDisplayName },
    )

    /**
     * Returns a copy with [email] updated and any previous [emailError] cleared.
     */
    fun withEmail(newEmail: String): RegisterFormState = copy(
        email = newEmail,
        emailError = null,
        error = error?.takeUnless { it == SignInResult.InvalidEmail },
    )

    /**
     * Returns a copy with [password] updated and any previous [passwordError] cleared.
     */
    fun withPassword(newPassword: String): RegisterFormState = copy(
        password = newPassword,
        passwordError = null,
        error = error?.takeUnless { it == SignInResult.WeakPassword },
    )

    /**
     * Returns a copy switched to [newMode] with transient form-level errors cleared.
     */
    fun withMode(newMode: RegisterMode): RegisterFormState = copy(
        mode = newMode,
        error = null,
    )

    /**
     * Switches the form to explicit completion mode ([RegisterMode.CompleteExisting]).
     */
    fun selectCompletionMode(): RegisterFormState = withMode(RegisterMode.CompleteExisting)

    /**
     * Switches the form to account creation mode ([RegisterMode.CreateAccount]).
     */
    fun selectCreateAccountMode(): RegisterFormState = withMode(RegisterMode.CreateAccount)

    /**
     * Evaluates local field validation rules and marks any blank required field with its
     * corresponding safe [SignInResult] field error while preserving the current [password]
     * exactly as entered.
     */
    fun validate(): RegisterFormState {
        val nextDisplayNameError = if (isDisplayNameValid) null else SignInResult.InvalidDisplayName
        val nextEmailError = if (isEmailValid) null else SignInResult.InvalidEmail
        val nextPasswordError = if (isPasswordValid) null else SignInResult.WeakPassword
        val firstFieldError = nextDisplayNameError ?: nextEmailError ?: nextPasswordError

        return copy(
            displayNameError = nextDisplayNameError,
            emailError = nextEmailError,
            passwordError = nextPasswordError,
            error = firstFieldError,
        )
    }

    /**
     * Returns a copy of this state with the transient [password] cleared (reset to empty string),
     * while retaining [displayName], [email], [mode], [isSubmitting], and errors.
     */
    fun clearPassword(): RegisterFormState = copy(password = "")

    /**
     * Returns a copy of this state with all field and form errors cleared.
     */
    fun clearErrors(): RegisterFormState = copy(
        error = null,
        displayNameError = null,
        emailError = null,
        passwordError = null,
    )

    /**
     * Returns a copy of this state configured for starting a submission attempt.
     * Sets [isSubmitting] to true and clears previous field and form errors.
     */
    fun onAttemptStarted(): RegisterFormState = copy(
        isSubmitting = true,
        error = null,
        displayNameError = null,
        emailError = null,
        passwordError = null,
    )

    /**
     * Returns a copy of this state updated for when a registration or completion attempt ends.
     *
     * Preserves approved fields ([displayName], [email], and [mode]) in memory, clears the transient
     * [password], sets [isSubmitting] to false, routes identifiable field errors to their respective
     * field properties, and transitions [mode] to [RegisterMode.CompleteExisting] if Firebase account
     * creation already succeeded before a correctable profile error ([SignInResult.InvalidDisplayName]).
     */
    fun onAttemptEnded(outcomeError: SignInResult? = null): RegisterFormState {
        val failure = outcomeError?.takeIf { !it.isSuccess }
        val nextMode = if (failure == SignInResult.InvalidDisplayName) {
            RegisterMode.CompleteExisting
        } else {
            mode
        }
        return copy(
            password = "",
            mode = nextMode,
            isSubmitting = false,
            error = failure,
            displayNameError = failure.takeIf { it == SignInResult.InvalidDisplayName },
            emailError = failure.takeIf { it == SignInResult.InvalidEmail },
            passwordError = failure.takeIf { it == SignInResult.WeakPassword },
        )
    }

    /**
     * Custom [toString] implementation to prevent exposing raw passwords in state dumps,
     * logcat, or diagnostic outputs.
     */
    override fun toString(): String {
        val maskedPassword = if (password.isEmpty()) "<empty>" else "[REDACTED]"
        return "RegisterFormState(" +
            "displayName='$displayName', " +
            "email='$email', " +
            "password='$maskedPassword', " +
            "mode=$mode, " +
            "isSubmitting=$isSubmitting, " +
            "error=$error, " +
            "displayNameError=$displayNameError, " +
            "emailError=$emailError, " +
            "passwordError=$passwordError" +
            ")"
    }
}
