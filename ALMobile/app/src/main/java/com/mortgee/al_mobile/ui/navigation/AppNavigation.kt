package com.mortgee.al_mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mortgee.al_mobile.ui.catalogue.PropertyCatalogueRoute
import com.mortgee.al_mobile.ui.detail.PropertyDetailRoute
import com.mortgee.al_mobile.ui.testfeature.TestFeatureRoute

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    isTestFeatureEnabled: Boolean = NavigationConfig.ENABLE_TEST_FEATURE
) {
    var currentDestination by remember { mutableStateOf<NavDestination>(NavDestination.PropertyCatalogue) }

    when (val destination = currentDestination) {
        is NavDestination.PropertyCatalogue -> {
            PropertyCatalogueRoute(
                onPropertyClick = { propertyId ->
                    currentDestination = NavDestination.PropertyDetail(propertyId)
                },
                isTestFeatureEnabled = isTestFeatureEnabled,
                onTestFeatureClick = if (isTestFeatureEnabled) {
                    { currentDestination = NavDestination.TestFeature }
                } else null
            )
        }
        is NavDestination.PropertyDetail -> {
            PropertyDetailRoute(
                propertyId = destination.propertyId,
                onBackClick = { currentDestination = NavDestination.PropertyCatalogue }
            )
        }
        is NavDestination.TestFeature -> {
            if (isTestFeatureEnabled) {
                TestFeatureRoute(
                    onBackClick = { currentDestination = NavDestination.PropertyCatalogue }
                )
            } else {
                // Fallback to catalogue if feature flag is disabled mid-navigation
                currentDestination = NavDestination.PropertyCatalogue
                PropertyCatalogueRoute(
                    onPropertyClick = { propertyId ->
                        currentDestination = NavDestination.PropertyDetail(propertyId)
                    },
                    isTestFeatureEnabled = false
                )
            }
        }
    }
}
