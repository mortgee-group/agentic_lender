package com.mortgee.al_mobile.ui.register

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route composable for the Borrower registration screen.
 *
 * Resolves [RegisterViewModel] via Hilt injection, collects [RegisterFormState] using the
 * project's lifecycle-aware [collectAsStateWithLifecycle] convention, and forwards user events
 * (display name updates, email updates, password updates, mode selection, and form submission)
 * to [RegisterScreen] without triggering automatic submission on composition or rotation.
 *
 * When navigated to from guest navigation with a positive [entryGeneration], initializes the form
 * in [initialMode] ([RegisterMode.CreateAccount] or [RegisterMode.CompleteExisting]) while preserving
 * in-memory state and active submissions across configuration changes. Clears the form when
 * navigating back to Sign-In so Back cannot recover a previously entered or submitted form.
 */
@Composable
fun RegisterRoute(
    modifier: Modifier = Modifier,
    initialMode: RegisterMode = RegisterMode.CreateAccount,
    entryGeneration: Int = 0,
    onNavigateToSignIn: (() -> Unit)? = null,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    viewModel.applyNavigationEntry(
        entryGeneration = entryGeneration,
        initialMode = initialMode,
    )

    val formState by viewModel.formState.collectAsStateWithLifecycle()

    val handleNavigateToSignIn: (() -> Unit)? = onNavigateToSignIn?.let { navigate ->
        {
            if (!formState.isSubmitting) {
                viewModel.resetForm()
                navigate()
            }
        }
    }

    BackHandler(enabled = handleNavigateToSignIn != null) {
        handleNavigateToSignIn?.invoke()
    }

    RegisterScreen(
        formState = formState,
        onFirstNameChange = viewModel::onFirstNameChanged,
        onLastNameChange = viewModel::onLastNameChanged,
        onPhoneNumberChange = viewModel::onPhoneNumberChanged,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
        onSelectCompletionMode = viewModel::selectCompletionMode,
        onSelectCreateAccountMode = viewModel::selectCreateAccountMode,
        onNavigateToSignIn = handleNavigateToSignIn,
        modifier = modifier,
    )
}
