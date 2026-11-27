package com.mortgee.al_mobile.ui.loan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoanValidationTest {

    @Test
    fun moneyParsing() {
        assertEquals(75000.1, loanMoney(" 75000.10 "))
        assertEquals(0.0, loanMoney("0"))
        assertEquals(0.0, loanMoney("0.00"))
        assertEquals(0.01, loanMoney("0.01"))
        assertEquals(350000.0, loanMoney("350000"))
        assertEquals(1234.56, loanMoney("1234.56"))

        assertNull(loanMoney(""))
        assertNull(loanMoney("   "))
        assertNull(loanMoney("-1"))
        assertNull(loanMoney("+100"))
        assertNull(loanMoney("1,000"))
        assertNull(loanMoney("1.001"))
        assertNull(loanMoney(".50"))
        assertNull(loanMoney("10."))
        assertNull(loanMoney("NaN"))
        assertNull(loanMoney("Infinity"))
        assertNull(loanMoney("1e3"))
        assertNull(loanMoney("9".repeat(400)))
        assertNull(loanMoney("9007199254740993"))
    }
}
