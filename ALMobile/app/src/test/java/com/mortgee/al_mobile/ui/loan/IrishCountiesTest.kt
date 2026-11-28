package com.mortgee.al_mobile.ui.loan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrishCountiesTest {

    @Test
    fun emptyQueryReturns32UniqueFormattedLabelsInOrder() {
        val results = IrishCounties.search("")
        assertEquals(32, results.size)
        assertEquals(32, results.toSet().size)
        assertEquals("co. Antrim", results.first())
        assertEquals("co. Derry", results[6])
        assertEquals("co. Wicklow", results.last())
        assertEquals( results, IrishCounties.search("   ") )
    }

    @Test
    fun searchMatchesCaseInsensitivelyOnBareCountyNameAndTrimsQuery() {
        val expectedC = listOf(
            "co. Carlow",
            "co. Cavan",
            "co. Clare",
            "co. Cork",
            "co. Limerick",
            "co. Roscommon",
            "co. Wicklow",
        )
        assertEquals(expectedC, IrishCounties.search("c"))
        assertEquals(expectedC, IrishCounties.search("C"))
        assertEquals(expectedC, IrishCounties.search("  c  "))
    }

    @Test
    fun searchSubstringMatchesOnlyRelevantCounty() {
        assertEquals(listOf("co. Cork"), IrishCounties.search("ork"))
        assertEquals(listOf("co. Cork"), IrishCounties.search("  ORK "))
        assertTrue(IrishCounties.search("co.").isEmpty())
    }

    @Test
    fun isSelectionChecksExactMembershipIn32FormattedLabels() {
        assertTrue(IrishCounties.isSelection("co. Cork"))
        assertTrue(IrishCounties.isSelection("co. Derry"))
        assertFalse(IrishCounties.isSelection("Cork"))
        assertFalse(IrishCounties.isSelection("CO. Cork"))
        assertFalse(IrishCounties.isSelection("co. cork"))
        assertFalse(IrishCounties.isSelection("co. Cork "))
        assertFalse(IrishCounties.isSelection("co. Londonderry"))
        assertFalse(IrishCounties.isSelection(""))

        for (label in IrishCounties.search("")) {
            assertTrue("Expected $label to be a valid selection", IrishCounties.isSelection(label))
        }
    }
}
