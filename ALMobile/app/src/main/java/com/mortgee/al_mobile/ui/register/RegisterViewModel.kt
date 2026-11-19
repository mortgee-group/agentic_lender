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
 * display name upon submit, sets busy status synchronously prior to launching coroutine work,
 * selects either [AccountRepository.register] or [AccountRepository.completeRegistration]
 * according to [RegisterMode], and clears transient password state upon completion.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    /**
     * Updates the display name input field in [formState] and clears any active display name error.
     */
    fun updateDisplayName(displayName: String) {
        _formState.update { it.withDisplayName(displayName) }
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
     * Convenience callback alias for updating display name.
     */
    fun onDisplayNameChanged(displayName: String) = updateDisplayName(displayName)

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
     * - Validates local input rules via [RegisterFormState.validate] and marks blank fields without
     *   calling the repository or modifying the entered password.
     * - Snapshots [RegisterFormState.displayName], [RegisterFormState.email], [RegisterFormState.password],
     *   and [RegisterFormState.mode] and sets [RegisterFormState.isSubmitting] synchronously before launching
     *   the coroutine.
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

        val submittedDisplayName = validatedState.displayName
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
                        displayName = submittedDisplayName,
                    )
                } else {
                    accountRepository.register(
                        email = submittedEmail,
                        password = submittedPassword,
                        displayName = submittedDisplayName,
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
