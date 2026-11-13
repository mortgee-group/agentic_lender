package com.mortgee.al_mobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendRoleTest {

    @Test
    fun `supported backend roles map correctly from exact string values`() {
        assertEquals(BackendRole.BORROWER, BackendRole.fromValue("borrower"))
        assertEquals(BackendRole.BROKER, BackendRole.fromValue("broker"))
        assertEquals(BackendRole.UNDERWRITER, BackendRole.fromValue("underwriter"))
    }

    @Test
    fun `backend role enum values match exact backend strings`() {
        assertEquals("borrower", BackendRole.BORROWER.value)
        assertEquals("broker", BackendRole.BROKER.value)
        assertEquals("underwriter", BackendRole.UNDERWRITER.value)
    }

    @Test
    fun `unknown values return null and do not default to borrower`() {
        assertNull(BackendRole.fromValue("owner"))
        assertNull(BackendRole.fromValue("provider"))
        assertNull(BackendRole.fromValue("admin"))
        assertNull(BackendRole.fromValue("BORROWER")) // case-sensitive exact match
        assertNull(BackendRole.fromValue(""))
        assertNull(BackendRole.fromValue(null))
    }
}
