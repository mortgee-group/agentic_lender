package com.mortgee.al_mobile.ui.signin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route composable for the Sign-In screen.
 *
 * Obtains [SignInViewModel] via Hilt injection, collects [SignInFormState] with lifecycle safety,
 * and forwards user events (email update, password update, submission, and guest navigation to
 * Borrower registration or explicit registration completion) to [SignInScreen].
 */
@Composable
fun SignInRoute(
    modifier: Modifier = Modifier,
    onNavigateToRegister: (() -> Unit)? = null,
    onNavigateToFinishRegistration: (() -> Unit)? = null,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    val handleNavigateToRegister: (() -> Unit)? = onNavigateToRegister?.let { navigate ->
        {
            if (!formState.isSubmitting) {
                viewModel.clearError()
                navigate()
            }
        }
    }

    val handleNavigateToFinishRegistration: (() -> Unit)? = onNavigateToFinishRegistration?.let { navigate ->
        {
            if (!formState.isSubmitting) {
                viewModel.clearError()
                navigate()
            }
        }
    }

    SignInScreen(
        formState = formState,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
        onNavigateToRegister = handleNavigateToRegister,
        onNavigateToFinishRegistration = handleNavigateToFinishRegistration,
        modifier = modifier
    )
}
