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
     * Explicit table mapping supported backend roles to existing destinations for the MVP.
     * MVP Decision: Application is borrower only for MVP.
     * - [BackendRole.BORROWER] maps to [NavDestination.PropertyCatalogue].
     * - [BackendRole.BROKER] and [BackendRole.UNDERWRITER] have no private destination in MVP.
     * - Unknown roles and null have no private destination and do not fall back to Borrower.
     * - Owner and Provider have no approved backend mapping.
     */
    fun getDestinationForRole(role: BackendRole?): NavDestination? {
        return when (role) {
            BackendRole.BORROWER -> NavDestination.PropertyCatalogue
            BackendRole.BROKER,
            BackendRole.UNDERWRITER -> null
            null -> null
        }
    }
}

/**
 * Navigation destinations for the ALMobile app.
 */
sealed interface NavDestination {
    data object PropertyCatalogue : NavDestination
    data class PropertyDetail(val propertyId: String) : NavDestination
    data object TestFeature : NavDestination
}
