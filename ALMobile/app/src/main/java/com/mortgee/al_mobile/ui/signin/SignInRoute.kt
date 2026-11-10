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
 * and forwards user events (email update, password update, submission) to [SignInScreen].
 */
@Composable
fun SignInRoute(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    SignInScreen(
        formState = formState,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
        modifier = modifier
    )
}
