package com.mortgee.al_mobile.ui.landing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale

/**
 * Stateful route composable for the shared authenticated landing screen.
 *
 * @param onSignOut Optional callback when the user requests to sign out or exit the session.
 */
@Composable
@Preview
fun AuthenticatedLandingRoute(
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null,
) {
    AuthenticatedLandingScreen(
        onSignOut = onSignOut,
        modifier = modifier
    )
}
