package com.mortgee.al_mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.ui.catalogue.PropertyCatalogueRoute
import com.mortgee.al_mobile.ui.detail.PropertyDetailRoute
import com.mortgee.al_mobile.ui.landing.AuthenticatedLandingRoute
import com.mortgee.al_mobile.ui.signin.SignInRoute
import com.mortgee.al_mobile.ui.testfeature.TestFeatureRoute
import kotlinx.coroutines.launch

/**
 * Main application navigation component.
 *
 * Gates private route entry on an active [SessionState.Authenticated] session.
 * Keys destination state and private back stack to the active session generation ([SessionKey]).
 * Directs unauthenticated or invalid session states to [SignInRoute].
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    sessionOwner: SessionOwner? = null,
    sessionStateOverride: SessionState? = null,
    isTestFeatureEnabled: Boolean = NavigationConfig.ENABLE_TEST_FEATURE,
    onSignOut: (() -> Unit)? = null,
) {
    val collectedState = sessionOwner?.sessionState?.collectAsStateWithLifecycle()?.value
    val currentSessionState = sessionStateOverride ?: collectedState ?: SessionState.SignedOut

    val coroutineScope = rememberCoroutineScope()
    val handleSignOut: () -> Unit = {
        onSignOut?.invoke()
        sessionOwner?.let { owner ->
            coroutineScope.launch {
                owner.signOut()
            }
        }
    }

    val authenticatedState = currentSessionState as? SessionState.Authenticated
    val initialDestination = authenticatedState?.let {
        NavigationConfig.getDestinationForRole(it.role)
    }

    if (authenticatedState != null && initialDestination != null) {
        key(authenticatedState.key) {
            var currentDestination by remember(authenticatedState.key) {
                mutableStateOf<NavDestination>(initialDestination)
            }

            when (val destination = currentDestination) {
                is NavDestination.AuthenticatedLanding -> {
                    AuthenticatedLandingRoute(
                        onSignOut = handleSignOut,
                        modifier = modifier
                    )
                }
                is NavDestination.PropertyCatalogue -> {
                    PropertyCatalogueRoute(
                        onPropertyClick = { propertyId ->
                            currentDestination = NavDestination.PropertyDetail(propertyId)
                        },
                        isTestFeatureEnabled = isTestFeatureEnabled,
                        onTestFeatureClick = if (isTestFeatureEnabled) {
                            { currentDestination = NavDestination.TestFeature }
                        } else null,
                        onSignOut = handleSignOut
                    )
                }
                is NavDestination.PropertyDetail -> {
                    PropertyDetailRoute(
                        propertyId = destination.propertyId,
                        onBackClick = { currentDestination = initialDestination }
                    )
                }
                is NavDestination.TestFeature -> {
                    if (isTestFeatureEnabled) {
                        TestFeatureRoute(
                            onBackClick = { currentDestination = initialDestination }
                        )
                    } else {
                        currentDestination = initialDestination
                        PropertyCatalogueRoute(
                            onPropertyClick = { propertyId ->
                                currentDestination = NavDestination.PropertyDetail(propertyId)
                            },
                            isTestFeatureEnabled = false,
                            onSignOut = handleSignOut
                        )
                    }
                }
            }
        }
    } else {
        SignInRoute(
            modifier = modifier
        )
    }
}
