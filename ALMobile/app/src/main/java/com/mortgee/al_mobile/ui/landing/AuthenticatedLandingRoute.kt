package com.mortgee.al_mobile.ui.landing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.ui.loan.LoanApplicationRoute

/**
 * Stateful route composable for the shared authenticated landing screen.
 *
 * @param authenticatedState Optional active authenticated session state containing user details.
 * @param onSignOut Optional callback when the user requests to sign out or exit the session.
 */
@Composable
@Preview
fun AuthenticatedLandingRoute(
    modifier: Modifier = Modifier,
    authenticatedState: SessionState.Authenticated? = null,
    onSignOut: (() -> Unit)? = null,
) {
    if (authenticatedState?.role == BackendRole.BORROWER) {
        LoanApplicationRoute(
            authenticatedState = authenticatedState,
            onSignOut = onSignOut,
            modifier = modifier,
        )
    } else {
        AuthenticatedLandingScreen(
            authenticatedState = authenticatedState,
            onSignOut = onSignOut,
            modifier = modifier
        )
    }
}
