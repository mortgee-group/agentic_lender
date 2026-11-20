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
 * Exposes [firstName], [lastName], optional [phoneNumber], [email], transient [password],
 * field/form error outcomes, and [mode].
 * Preserves [firstName], [lastName], [phoneNumber], [email], and [mode] in memory across
 * configuration changes and after recoverable failures while clearing [password] when a
 * submission attempt ends.
 * Defaults to an empty form on process recreation.
 *
 * Overrides [toString] to ensure sensitive credentials ([password]) are masked in state dumps,
 * logs, and diagnostic outputs.
 */
data class RegisterFormState(
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val password: String = "",
    val mode: RegisterMode = RegisterMode.CreateAccount,
    val isSubmitting: Boolean = false,
    val error: SignInResult? = null,
    val firstNameError: SignInResult? = null,
    val lastNameError: SignInResult? = null,
    val phoneNumberError: SignInResult? = null,
    val emailError: SignInResult? = null,
    val passwordError: SignInResult? = null,
) {
    /**
     * True if [firstName] is non-blank and within the backend model limit (1–30 characters).
     */
    val isFirstNameValid: Boolean
        get() = firstName.isNotBlank() && firstName.length <= MAX_FIRST_NAME_LENGTH

    /**
     * True if [lastName] is non-blank and within the backend model limit (1–50 characters).
     */
    val isLastNameValid: Boolean
        get() = lastName.isNotBlank() && lastName.length <= MAX_LAST_NAME_LENGTH

    /**
     * True if optional [phoneNumber] is blank or within the backend model limit (max 20 characters).
     */
    val isPhoneNumberValid: Boolean
        get() = phoneNumber.isBlank() || phoneNumber.length <= MAX_PHONE_NUMBER_LENGTH

    /**
     * Normalized optional phone number (`null` when blank, or the string when non-blank).
     */
    val normalizedPhoneNumber: String?
        get() = phoneNumber.takeIf { it.isNotBlank() }

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
     * True if all profile fields ([firstName], [lastName], and optional [phoneNumber]) satisfy
     * the `UserProfileCreate` length and presence constraints.
     */
    val areProfileFieldsValid: Boolean
        get() = isFirstNameValid && isLastNameValid && isPhoneNumberValid

    /**
     * True if all inputs required for the active [mode] are valid:
     * - Fresh registration ([RegisterMode.CreateAccount]): checks profile fields ([firstName],
     *   [lastName], optional [phoneNumber]) and credentials ([email], [password]) before Firebase creation.
     * - Explicit completion ([RegisterMode.CompleteExisting]): requires [email] and [password] for
     *   authentication and `GET /users/me`, plus valid optional [phoneNumber] length if supplied
     *   (missing profile `GET 404` checks required profile fields before allowing a completion `POST`).
     */
    val inputIsValid: Boolean
        get() = if (isCompletionMode) {
            isEmailValid && isPasswordValid && isPhoneNumberValid &&
                (firstName.isBlank() || isFirstNameValid) &&
                (lastName.isBlank() || isLastNameValid)
        } else {
            areProfileFieldsValid && isEmailValid && isPasswordValid
        }

    /**
     * True if the form can be submitted (all inputs for the current mode are valid and no submission is in progress).
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
            it == SignInResult.InvalidFirstName ||
                it == SignInResult.InvalidLastName ||
                it == SignInResult.InvalidPhoneNumber ||
                it == SignInResult.InvalidDisplayName ||
                it == SignInResult.InvalidEmail ||
                it == SignInResult.WeakPassword
        }

    /**
     * Returns a copy with [firstName] updated and any previous [firstNameError] cleared.
     */
    fun withFirstName(newFirstName: String): RegisterFormState = copy(
        firstName = newFirstName,
        firstNameError = null,
        error = error?.takeUnless {
            it == SignInResult.InvalidFirstName || it == SignInResult.InvalidDisplayName
        },
    )

    /**
     * Returns a copy with [lastName] updated and any previous [lastNameError] cleared.
     */
    fun withLastName(newLastName: String): RegisterFormState = copy(
        lastName = newLastName,
        lastNameError = null,
        error = error?.takeUnless { it == SignInResult.InvalidLastName },
    )

    /**
     * Returns a copy with [phoneNumber] updated and any previous [phoneNumberError] cleared.
     */
    fun withPhoneNumber(newPhoneNumber: String): RegisterFormState = copy(
        phoneNumber = newPhoneNumber,
        phoneNumberError = null,
        error = error?.takeUnless { it == SignInResult.InvalidPhoneNumber },
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
     * Evaluates local field validation rules and marks any invalid field with its
     * corresponding safe [SignInResult] field error while preserving the current [password]
     * exactly as entered.
     *
     * Fresh registration ([RegisterMode.CreateAccount]) requires valid [firstName] (1–30),
     * [lastName] (1–50), optional [phoneNumber] (<= 20), [email], and [password] before
     * creating a Firebase account.
     * Explicit completion ([RegisterMode.CompleteExisting]) requires [email] and [password]
     * before reading the profile, while rejecting overlength name/phone inputs locally and
     * requiring valid profile fields only if `GET /users/me` confirms a missing profile (`404`).
     */
    fun validate(): RegisterFormState {
        val nextFirstNameError = when {
            !isCompletionMode && !isFirstNameValid -> SignInResult.InvalidFirstName
            isCompletionMode && firstName.isNotBlank() && !isFirstNameValid -> SignInResult.InvalidFirstName
            else -> null
        }
        val nextLastNameError = when {
            !isCompletionMode && !isLastNameValid -> SignInResult.InvalidLastName
            isCompletionMode && lastName.isNotBlank() && !isLastNameValid -> SignInResult.InvalidLastName
            else -> null
        }
        val nextPhoneNumberError = if (isPhoneNumberValid) null else SignInResult.InvalidPhoneNumber
        val nextEmailError = if (isEmailValid) null else SignInResult.InvalidEmail
        val nextPasswordError = if (isPasswordValid) null else SignInResult.WeakPassword

        val firstFieldError = nextFirstNameError
            ?: nextLastNameError
            ?: nextPhoneNumberError
            ?: nextEmailError
            ?: nextPasswordError

        return copy(
            firstNameError = nextFirstNameError,
            lastNameError = nextLastNameError,
            phoneNumberError = nextPhoneNumberError,
            emailError = nextEmailError,
            passwordError = nextPasswordError,
            error = firstFieldError,
        )
    }

    /**
     * Returns a copy of this state with the transient [password] cleared (reset to empty string),
     * while retaining [firstName], [lastName], [phoneNumber], [email], [mode], [isSubmitting], and errors.
     */
    fun clearPassword(): RegisterFormState = copy(password = "")

    /**
     * Returns a copy of this state with all field and form errors cleared.
     */
    fun clearErrors(): RegisterFormState = copy(
        error = null,
        firstNameError = null,
        lastNameError = null,
        phoneNumberError = null,
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
        firstNameError = null,
        lastNameError = null,
        phoneNumberError = null,
        emailError = null,
        passwordError = null,
    )

    /**
     * Returns a copy of this state updated for when a registration or completion attempt ends.
     *
     * Preserves approved fields ([firstName], [lastName], [phoneNumber], [email], and [mode]) in memory,
     * clears the transient [password], sets [isSubmitting] to false, routes identifiable field errors
     * to their respective field properties, and transitions [mode] to [RegisterMode.CompleteExisting]
     * if Firebase account creation already succeeded before a correctable profile error
     * ([SignInResult.InvalidFirstName], [SignInResult.InvalidLastName], [SignInResult.InvalidPhoneNumber],
     * or [SignInResult.InvalidDisplayName]).
     */
    fun onAttemptEnded(outcomeError: SignInResult? = null): RegisterFormState {
        val failure = outcomeError?.takeIf { !it.isSuccess }
        val isCorrectableProfileError =
            failure == SignInResult.InvalidFirstName ||
                failure == SignInResult.InvalidLastName ||
                failure == SignInResult.InvalidPhoneNumber ||
                failure == SignInResult.InvalidDisplayName
        val nextMode = if (isCorrectableProfileError) {
            RegisterMode.CompleteExisting
        } else {
            mode
        }
        return copy(
            password = "",
            mode = nextMode,
            isSubmitting = false,
            error = failure,
            firstNameError = failure.takeIf {
                it == SignInResult.InvalidFirstName || it == SignInResult.InvalidDisplayName
            },
            lastNameError = failure.takeIf { it == SignInResult.InvalidLastName },
            phoneNumberError = failure.takeIf { it == SignInResult.InvalidPhoneNumber },
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
            "firstName='$firstName', " +
            "lastName='$lastName', " +
            "phoneNumber='$phoneNumber', " +
            "email='$email', " +
            "password='$maskedPassword', " +
            "mode=$mode, " +
            "isSubmitting=$isSubmitting, " +
            "error=$error, " +
            "firstNameError=$firstNameError, " +
            "lastNameError=$lastNameError, " +
            "phoneNumberError=$phoneNumberError, " +
            "emailError=$emailError, " +
            "passwordError=$passwordError" +
            ")"
    }

    companion object {
        const val MAX_FIRST_NAME_LENGTH = 30
        const val MAX_LAST_NAME_LENGTH = 50
        const val MAX_PHONE_NUMBER_LENGTH = 20
    }
}
