package com.mortgee.al_mobile.ui.loan

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.ui.landing.AuthenticatedLandingScreen

@Composable
fun LoanApplicationRoute(
    authenticatedState: SessionState.Authenticated,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: LoanApplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val key = authenticatedState.key

    if (state.phase == LoanPhase.CLOSED || state.owner != key) {
        AuthenticatedLandingScreen(
            authenticatedState = authenticatedState,
            onSignOut = onSignOut,
            onStartLoanApplication = { viewModel.open(key) },
            modifier = modifier,
        )
    } else {
        val handleBack: () -> Unit = {
            if (state.phase != LoanPhase.SENDING) {
                viewModel.close(key)
            }
        }

        BackHandler(enabled = true, onBack = handleBack)

        LoanApplicationScreen(
            state = state,
            onEdit = { field, text -> viewModel.edit(key, field, text) },
            onDebt = { yes -> viewModel.chooseDebt(key, yes) },
            onSubmit = { viewModel.submit(key) },
            onBack = handleBack,
            modifier = modifier,
        )
    }
}
