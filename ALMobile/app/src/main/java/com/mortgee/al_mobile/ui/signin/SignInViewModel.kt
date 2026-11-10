package com.mortgee.al_mobile.ui.signin

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
 * ViewModel for the Sign-In screen.
 *
 * Manages [SignInFormState], validates input fields, snapshots credentials upon submit,
 * sets busy status prior to coroutine execution, and delegates authentication to [AccountRepository].
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(SignInFormState())
    val formState: StateFlow<SignInFormState> = _formState.asStateFlow()

    /**
     * Updates the email input field in [formState].
     */
    fun updateEmail(email: String) {
        _formState.update { it.copy(email = email) }
    }

    /**
     * Updates the password input field in [formState].
     */
    fun updatePassword(password: String) {
        _formState.update { it.copy(password = password) }
    }

    /**
     * Convenience callback alias for updating email.
     */
    fun onEmailChanged(email: String) = updateEmail(email)

    /**
     * Convenience callback alias for updating password.
     */
    fun onPasswordChanged(password: String) = updatePassword(password)

    /**
     * Clears any active error from [formState].
     */
    fun clearError() {
        _formState.update { it.copy(error = null) }
    }

    /**
     * Resets the entire form to its initial empty state.
     */
    fun resetForm() {
        _formState.value = SignInFormState()
    }

    /**
     * Submits the credentials currently held in [formState] to [AccountRepository].
     *
     * Validates that required fields are present and that a submit attempt is not already in progress.
     * Snapshots email and password credentials and sets [SignInFormState.isSubmitting] synchronously
     * on the main thread prior to launching the async coroutine. Passes the password unchanged to the
     * repository and resets the submission state while clearing password in cleanup.
     */
    fun submit() {
        val currentState = _formState.value
        if (!currentState.canSubmit) {
            return
        }

        val submittedEmail = currentState.email
        val submittedPassword = currentState.password

        _formState.update { it.onAttemptStarted() }

        viewModelScope.launch {
            var result: SignInResult? = null
            try {
                result = accountRepository.signIn(submittedEmail, submittedPassword)
            } finally {
                _formState.update { state ->
                    state.onAttemptEnded(result?.takeIf { !it.isSuccess })
                }
            }
        }
    }
}
