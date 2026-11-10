package com.mortgee.al_mobile.ui.signin

import com.mortgee.al_mobile.data.model.SignInResult

/**
 * In-memory state holder for the sign-in form.
 *
 * Stores user credentials ([email] and [password]) and submission status in memory only.
 * Overrides [toString] to ensure sensitive credentials ([password]) are masked
 * in state dumps, log outputs, and diagnostics.
 */
data class SignInFormState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: SignInResult? = null,
) {
    /**
     * True if both email and password are non-blank.
     */
    val inputIsValid: Boolean
        get() = email.isNotBlank() && password.isNotBlank()

    /**
     * True if the form can be submitted (valid inputs and not currently submitting).
     */
    val canSubmit: Boolean
        get() = inputIsValid && !isSubmitting

    /**
     * Returns a copy of this state with the password cleared (reset to empty string),
     * while retaining [email], [isSubmitting], and [error].
     */
    fun clearPassword(): SignInFormState = copy(password = "")

    /**
     * Returns a copy of this state configured for starting a submission attempt.
     * Sets [isSubmitting] to true and clears previous [error].
     */
    fun onAttemptStarted(): SignInFormState = copy(
        isSubmitting = true,
        error = null,
    )

    /**
     * Returns a copy of this state updated for when a sign-in attempt ends,
     * retaining [email] and optional [outcomeError], while clearing [password]
     * and setting [isSubmitting] to false.
     */
    fun onAttemptEnded(outcomeError: SignInResult? = null): SignInFormState = copy(
        password = "",
        isSubmitting = false,
        error = outcomeError,
    )

    /**
     * Custom [toString] implementation to prevent exposing raw passwords in state dumps,
     * logcat, or diagnostic outputs.
     */
    override fun toString(): String {
        val maskedPassword = if (password.isEmpty()) "<empty>" else "[REDACTED]"
        return "SignInFormState(email='$email', password='$maskedPassword', isSubmitting=$isSubmitting, error=$error)"
    }
}
