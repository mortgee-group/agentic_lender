package com.mortgee.al_mobile.ui.register

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
 */
@Composable
fun RegisterRoute(
    modifier: Modifier = Modifier,
    onNavigateToSignIn: (() -> Unit)? = null,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    RegisterScreen(
        formState = formState,
        onDisplayNameChange = viewModel::onDisplayNameChanged,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
        onSelectCompletionMode = viewModel::selectCompletionMode,
        onSelectCreateAccountMode = viewModel::selectCreateAccountMode,
        onNavigateToSignIn = onNavigateToSignIn,
        modifier = modifier,
    )
}
