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

        assertTrue(catalogue is NavDestination.PropertyCatalogue)
        assertEquals("item_123", (detail as NavDestination.PropertyDetail).propertyId)
        assertTrue(testFeature is NavDestination.TestFeature)
    }

    @Test
    fun `getDestinationForRole maps borrower to PropertyCatalogue and others to null without fallback`() {
        // Borrower maps to PropertyCatalogue
        assertEquals(NavDestination.PropertyCatalogue, NavigationConfig.getDestinationForRole(BackendRole.BORROWER))

        // Broker and Underwriter have no private destination in MVP (returns null, no fallback to borrower)
        assertNull(NavigationConfig.getDestinationForRole(BackendRole.BROKER))
        assertNull(NavigationConfig.getDestinationForRole(BackendRole.UNDERWRITER))

        // Unknown / null role has no private destination and does not fall back to borrower
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
