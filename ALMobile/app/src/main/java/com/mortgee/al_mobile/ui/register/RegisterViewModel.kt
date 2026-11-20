package com.mortgee.al_mobile.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Borrower registration screen.
 *
 * Manages [RegisterFormState], validates input fields, snapshots transient credentials and
 * profile fields ([RegisterFormState.firstName], [RegisterFormState.lastName], and optional
 * [RegisterFormState.phoneNumber]) upon submit, sets busy status synchronously prior to launching
 * coroutine work, selects either [AccountRepository.register] or
 * [AccountRepository.completeRegistration] according to [RegisterMode], and clears transient
 * password state upon completion.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    /**
     * Updates the first name input field in [formState] and clears any active first name error.
     */
    fun updateFirstName(firstName: String) {
        _formState.update { it.withFirstName(firstName) }
    }

    /**
     * Updates the last name input field in [formState] and clears any active last name error.
     */
    fun updateLastName(lastName: String) {
        _formState.update { it.withLastName(lastName) }
    }

    /**
     * Updates the optional phone number input field in [formState] and clears any active phone number error.
     */
    fun updatePhoneNumber(phoneNumber: String) {
        _formState.update { it.withPhoneNumber(phoneNumber) }
    }

    /**
     * Updates the email input field in [formState] and clears any active email error.
     */
    fun updateEmail(email: String) {
        _formState.update { it.withEmail(email) }
    }

    /**
     * Updates the password input field in [formState] and clears any active password error.
     */
    fun updatePassword(password: String) {
        _formState.update { it.withPassword(password) }
    }

    /**
     * Convenience callback alias for updating first name.
     */
    fun onFirstNameChanged(firstName: String) = updateFirstName(firstName)

    /**
     * Convenience callback alias for updating last name.
     */
    fun onLastNameChanged(lastName: String) = updateLastName(lastName)

    /**
     * Convenience callback alias for updating optional phone number.
     */
    fun onPhoneNumberChanged(phoneNumber: String) = updatePhoneNumber(phoneNumber)

    /**
     * Convenience callback alias for updating email.
     */
    fun onEmailChanged(email: String) = updateEmail(email)

    /**
     * Convenience callback alias for updating password.
     */
    fun onPasswordChanged(password: String) = updatePassword(password)

    /**
     * Switches the form to the specified [mode] while clearing transient form-level errors.
     */
    fun updateMode(mode: RegisterMode) {
        _formState.update { it.withMode(mode) }
    }

    /**
     * Switches the form to explicit completion mode ([RegisterMode.CompleteExisting], "Finish registration").
     */
    fun selectCompletionMode() {
        _formState.update { it.selectCompletionMode() }
    }

    /**
     * Switches the form to standard account creation mode ([RegisterMode.CreateAccount]).
     */
    fun selectCreateAccountMode() {
        _formState.update { it.selectCreateAccountMode() }
    }

    /**
     * Clears any active field and form errors from [formState].
     */
    fun clearError() {
        _formState.update { it.clearErrors() }
    }

    private var lastAppliedEntryGeneration: Int = 0

    /**
     * Applies a fresh navigation entry generation when entering [RegisterRoute] from guest navigation.
     *
     * Resets [formState] to an empty [RegisterFormState] configured with [initialMode] once per
     * distinct positive [entryGeneration], while remaining a no-op across configuration changes
     * (such as screen rotation) for the same [entryGeneration].
     */
    internal fun applyNavigationEntry(
        entryGeneration: Int,
        initialMode: RegisterMode,
    ) {
        if (entryGeneration <= 0 || entryGeneration == lastAppliedEntryGeneration) {
            return
        }
        lastAppliedEntryGeneration = entryGeneration
        _formState.value = RegisterFormState(mode = initialMode)
    }

    /**
     * Resets the entire form to its initial empty state.
     */
    fun resetForm() {
        _formState.value = RegisterFormState()
    }

    /**
     * Validates and submits the registration inputs currently held in [formState] to [AccountRepository].
     *
     * - Ignores duplicate submissions while [RegisterFormState.isSubmitting] is true.
     * - Validates local input rules via [RegisterFormState.validate] and marks invalid fields without
     *   calling the repository or modifying the entered password.
     * - Snapshots [RegisterFormState.firstName], [RegisterFormState.lastName],
     *   [RegisterFormState.normalizedPhoneNumber], [RegisterFormState.email],
     *   [RegisterFormState.password], and [RegisterFormState.mode] and sets
     *   [RegisterFormState.isSubmitting] synchronously before launching the coroutine.
     * - Calls [AccountRepository.completeRegistration] when in [RegisterMode.CompleteExisting], or
     *   [AccountRepository.register] when in [RegisterMode.CreateAccount].
     * - Clears the transient password and updates error/mode state in `finally`.
     */
    fun submit() {
        val currentState = _formState.value
        if (currentState.isSubmitting) {
            return
        }

        val validatedState = currentState.validate()
        if (!validatedState.inputIsValid) {
            _formState.value = validatedState
            return
        }

        val submittedFirstName = validatedState.firstName
        val submittedLastName = validatedState.lastName
        val submittedPhoneNumber = validatedState.normalizedPhoneNumber
        val submittedEmail = validatedState.email
        val submittedPassword = validatedState.password
        val isCompleting = validatedState.mode == RegisterMode.CompleteExisting

        _formState.value = validatedState.onAttemptStarted()

        viewModelScope.launch {
            var result: SignInResult? = null
            try {
                result = if (isCompleting) {
                    accountRepository.completeRegistration(
                        email = submittedEmail,
                        password = submittedPassword,
                        firstName = submittedFirstName,
                        lastName = submittedLastName,
                        phoneNumber = submittedPhoneNumber,
                    )
                } else {
                    accountRepository.register(
                        email = submittedEmail,
                        password = submittedPassword,
                        firstName = submittedFirstName,
                        lastName = submittedLastName,
                        phoneNumber = submittedPhoneNumber,
                    )
                }
            } finally {
                _formState.update { state ->
                    state.onAttemptEnded(result)
                }
            }
        }
    }
}
