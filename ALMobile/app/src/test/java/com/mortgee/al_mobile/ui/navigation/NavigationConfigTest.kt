package com.mortgee.al_mobile.ui.navigation

import com.mortgee.al_mobile.data.model.BackendRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationConfigTest {

    @Test
    fun `NavigationConfig ENABLE_TEST_FEATURE can be toggled by developer`() {
        NavigationConfig.ENABLE_TEST_FEATURE = true
        assertTrue(NavigationConfig.ENABLE_TEST_FEATURE)

        NavigationConfig.ENABLE_TEST_FEATURE = false
        assertFalse(NavigationConfig.ENABLE_TEST_FEATURE)

        // Reset back to true for default
        NavigationConfig.ENABLE_TEST_FEATURE = true
        assertTrue(NavigationConfig.ENABLE_TEST_FEATURE)
    }

    @Test
    fun `NavDestination types instantiate correctly`() {
        val catalogue = NavDestination.PropertyCatalogue
        val detail = NavDestination.PropertyDetail("item_123")
        val testFeature = NavDestination.TestFeature
        val landing = NavDestination.AuthenticatedLanding

        assertTrue(catalogue is NavDestination.PropertyCatalogue)
        assertEquals("item_123", (detail as NavDestination.PropertyDetail).propertyId)
        assertTrue(testFeature is NavDestination.TestFeature)
        assertTrue(landing is NavDestination.AuthenticatedLanding)
    }

    @Test
    fun `getDestinationForRole maps all supported roles to AuthenticatedLanding and unknown to null`() {
        // Borrower, Broker, and Underwriter map to AuthenticatedLanding
        assertEquals(NavDestination.AuthenticatedLanding, NavigationConfig.getDestinationForRole(BackendRole.BORROWER))
        assertEquals(NavDestination.AuthenticatedLanding, NavigationConfig.getDestinationForRole(BackendRole.BROKER))
        assertEquals(NavDestination.AuthenticatedLanding, NavigationConfig.getDestinationForRole(BackendRole.UNDERWRITER))

        // Unknown / null role has no private destination
        assertNull(NavigationConfig.getDestinationForRole(null))
        val parsedUnknown = BackendRole.fromValue("unknown_role")
        assertNull(NavigationConfig.getDestinationForRole(parsedOwnerOrProvider(parsedUnknown)))
    }

    private fun parsedOwnerOrProvider(role: BackendRole?): BackendRole? {
        return role // null
    }

    private fun assertEquals(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
