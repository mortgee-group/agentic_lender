package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoanDraftTest {

    @Test
    fun distinctPathsAndExactMappings() {
        val fields = LoanField.entries
        assertEquals(12, fields.size)
        val paths = fields.map { it.path }
        assertEquals(12, paths.toSet().size)

        assertEquals("householdAnnualGrossIncome", LoanField.INCOME.path)
        assertEquals("existingDebtLiabilities", LoanField.DEBT.path)
        assertEquals("deposit", LoanField.DEPOSIT.path)
        assertEquals("requestedMortgageTerm", LoanField.TERM.path)
        assertEquals("sellerAskingPrice", LoanField.PRICE.path)
        assertEquals("property.county", LoanField.COUNTY.path)
        assertEquals("property.floor_area", LoanField.AREA.path)
        assertEquals("property.bedrooms", LoanField.BEDS.path)
        assertEquals("property.bathrooms", LoanField.BATHS.path)
        assertEquals("property.year_built", LoanField.YEAR.path)
        assertEquals("property.property_type", LoanField.TYPE.path)
        assertEquals("property.ber_rating", LoanField.BER.path)
    }

    @Test
    fun debtChoiceAndHiddenDebtInputTransition() {
        val initial = LoanDraft()
        assertNull(initial.hasDebt)
        for (field in LoanField.entries) {
            assertEquals("", initial[field])
        }

        // Ignoring DEBT edit while hasDebt is null
        val afterIgnoredWhileNull = initial.edit(LoanField.DEBT, "999")
        assertEquals("", afterIgnoredWhileNull[LoanField.DEBT])

        // Choose Yes, type debt "10000" and income "75000"
        val withDebtAndIncome = initial
            .chooseDebt(true)
            .edit(LoanField.DEBT, "10000")
            .edit(LoanField.INCOME, "75000")

        assertEquals(true, withDebtAndIncome.hasDebt)
        assertEquals("10000", withDebtAndIncome[LoanField.DEBT])
        assertEquals("75000", withDebtAndIncome[LoanField.INCOME])

        // Choose Yes again preserves visible debt amount
        val stillYes = withDebtAndIncome.chooseDebt(true)
        assertEquals(true, stillYes.hasDebt)
        assertEquals("10000", stillYes[LoanField.DEBT])

        // Choose No then Yes -> income "75000", debt "", hasDebt true
        val chosenNo = withDebtAndIncome.chooseDebt(false)
        assertEquals(false, chosenNo.hasDebt)
        assertEquals("", chosenNo[LoanField.DEBT])
        assertEquals("75000", chosenNo[LoanField.INCOME])

        // Calling edit(DEBT, "42") while No keeps the debt empty
        val ignoredWhileNo = chosenNo.edit(LoanField.DEBT, "42")
        assertEquals("", ignoredWhileNo[LoanField.DEBT])
        assertEquals(false, ignoredWhileNo.hasDebt)

        val backToYes = chosenNo.chooseDebt(true)
        assertEquals(true, backToYes.hasDebt)
        assertEquals("", backToYes[LoanField.DEBT])
        assertEquals("75000", backToYes[LoanField.INCOME])
    }
}
