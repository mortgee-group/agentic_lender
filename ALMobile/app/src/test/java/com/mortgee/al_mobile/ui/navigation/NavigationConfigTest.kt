package com.mortgee.al_mobile.ui.navigation

import org.junit.Assert.assertFalse
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

    private fun assertEquals(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
