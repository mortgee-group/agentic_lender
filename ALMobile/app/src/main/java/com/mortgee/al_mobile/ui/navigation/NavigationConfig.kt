package com.mortgee.al_mobile.ui.navigation

import com.mortgee.al_mobile.data.model.BackendRole

/**
 * Navigation feature flag configuration.
 * Toggle [ENABLE_TEST_FEATURE] manually to enable or disable the Test / Feature screen in navigation.
 */
object NavigationConfig {
    /**
     * Developer flag to switch the Test/Feature screen on or off in navigation.
     * Set to `true` to enable access to the Test/Feature page, or `false` to disable it.
     */
    var ENABLE_TEST_FEATURE: Boolean = true

    /**
     * Shared destination policy for Task 03.
     * Maps all three supported backend roles ([BackendRole.BORROWER], [BackendRole.BROKER], [BackendRole.UNDERWRITER])
     * to [NavDestination.AuthenticatedLanding].
     * Missing or unknown roles return null (no private destination).
     * Owner and Provider have no approved backend mapping.
     */
    fun getDestinationForRole(role: BackendRole?): NavDestination? {
        return when (role) {
            BackendRole.BORROWER,
            BackendRole.BROKER,
            BackendRole.UNDERWRITER -> NavDestination.AuthenticatedLanding
            null -> null
        }
    }
}

/**
 * Navigation destinations for the ALMobile app.
 */
sealed interface NavDestination {
    data object AuthenticatedLanding : NavDestination
    data object PropertyCatalogue : NavDestination
    data class PropertyDetail(val propertyId: String) : NavDestination
    data object TestFeature : NavDestination
}
