package com.mortgee.al_mobile.ui.navigation

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
}

/**
 * Navigation destinations for the ALMobile app.
 */
sealed interface NavDestination {
    data object PropertyCatalogue : NavDestination
    data class PropertyDetail(val propertyId: String) : NavDestination
    data object TestFeature : NavDestination
}
