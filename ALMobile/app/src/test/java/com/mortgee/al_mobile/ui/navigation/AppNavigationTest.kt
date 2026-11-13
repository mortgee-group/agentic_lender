package com.mortgee.al_mobile.ui.navigation

import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {

    @Test
    fun `unauthenticated states gate private content and require sign in`() {
        val signedOut = SessionState.SignedOut
        assertFalse(signedOut.isAuthenticated)
        assertNull(signedOut.sessionKey)

        val establishing = SessionState.Establishing("att_1")
        assertFalse(establishing.isAuthenticated)
        assertNull(establishing.sessionKey)

        val restoring = SessionState.Restoring("att_2")
        assertFalse(restoring.isAuthenticated)
        assertNull(restoring.sessionKey)
    }

    @Test
    fun `authenticated session for all supported roles maps to shared AuthenticatedLanding`() {
        val keyBorrower = SessionKey("user123", 1L)
        val stateBorrower = SessionState.Authenticated(
            key = keyBorrower,
            uid = "user123",
            email = "borrower@example.com",
            role = BackendRole.BORROWER
        )

        assertTrue(stateBorrower.isAuthenticated)
        assertEquals(keyBorrower, stateBorrower.sessionKey)
        assertEquals("user123", stateBorrower.currentUid)

        assertEquals(
            NavDestination.AuthenticatedLanding,
            NavigationConfig.getDestinationForRole(stateBorrower.role)
        )

        val keyBroker = SessionKey("broker123", 1L)
        val stateBroker = SessionState.Authenticated(
            key = keyBroker,
            uid = "broker123",
            email = "broker@example.com",
            role = BackendRole.BROKER
        )

        assertEquals(
            NavDestination.AuthenticatedLanding,
            NavigationConfig.getDestinationForRole(stateBroker.role)
        )

        val keyUnderwriter = SessionKey("uw123", 1L)
        val stateUnderwriter = SessionState.Authenticated(
            key = keyUnderwriter,
            uid = "uw123",
            email = "underwriter@example.com",
            role = BackendRole.UNDERWRITER
        )

        assertEquals(
            NavDestination.AuthenticatedLanding,
            NavigationConfig.getDestinationForRole(stateUnderwriter.role)
        )
    }

    @Test
    fun `unknown or null role returns null private destination`() {
        assertNull(NavigationConfig.getDestinationForRole(null))
        val unknownRole = BackendRole.fromValue("unknown_role")
        assertNull(NavigationConfig.getDestinationForRole(unknownRole))
    }

    @Test
    fun `distinct session generations produce non-equal session keys`() {
        val keyGen1 = SessionKey("user123", 1L)
        val keyGen2 = SessionKey("user123", 2L)
        val keyUserB = SessionKey("user456", 1L)

        assertFalse(keyGen1 == keyGen2)
        assertFalse(keyGen1 == keyUserB)
    }
}
