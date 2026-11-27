package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun financeRules() {
        val baseNoDebt = LoanDraft()
            .edit(LoanField.INCOME, "0")
            .chooseDebt(false)
            .edit(LoanField.DEPOSIT, "0")
            .edit(LoanField.TERM, "40")
            .edit(LoanField.PRICE, "350000")

        val validNoDebt = validateFinances(baseNoDebt)
        assertEquals(
            LoanFinances(
                income = 0.0,
                debt = 0.0,
                deposit = 0.0,
                term = 40,
                price = 350000.0,
            ),
            validNoDebt.value,
        )
        assertTrue(validNoDebt.errors.isEmpty())

        val validTermOneAndZeroDebtYes = baseNoDebt
            .chooseDebt(true)
            .edit(LoanField.DEBT, "0")
            .edit(LoanField.TERM, "1")
        val checkedTermOne = validateFinances(validTermOneAndZeroDebtYes)
        assertEquals(
            LoanFinances(
                income = 0.0,
                debt = 0.0,
                deposit = 0.0,
                term = 1,
                price = 350000.0,
            ),
            checkedTermOne.value,
        )
        assertTrue(checkedTermOne.errors.isEmpty())

        for (badTerm in listOf("0", "41", "1.5", "-5", "+10", "")) {
            val res = validateFinances(baseNoDebt.edit(LoanField.TERM, badTerm))
            assertNull(res.value)
            assertEquals(
                mapOf(LoanField.TERM to "Enter a whole number from 1 to 40."),
                res.errors,
            )
        }

        val equalDepositPrice = validateFinances(baseNoDebt.edit(LoanField.DEPOSIT, "350000"))
        assertNull(equalDepositPrice.value)
        assertEquals(
            mapOf(LoanField.DEPOSIT to "Deposit must be less than the asking price."),
            equalDepositPrice.errors,
        )

        val greaterDepositPrice = validateFinances(baseNoDebt.edit(LoanField.DEPOSIT, "350000.01"))
        assertNull(greaterDepositPrice.value)
        assertEquals(
            mapOf(LoanField.DEPOSIT to "Deposit must be less than the asking price."),
            greaterDepositPrice.errors,
        )

        val zeroPrice = validateFinances(baseNoDebt.edit(LoanField.PRICE, "0"))
        assertNull(zeroPrice.value)
        assertEquals(
            mapOf(LoanField.PRICE to "Enter an asking price greater than zero."),
            zeroPrice.errors,
        )

        val blankDebtUnderYes = validateFinances(baseNoDebt.chooseDebt(true))
        assertNull(blankDebtUnderYes.value)
        assertEquals(
            mapOf(LoanField.DEBT to "Enter a non-negative amount in euros."),
            blankDebtUnderYes.errors,
        )

        val emptyDraft = validateFinances(LoanDraft())
        assertNull(emptyDraft.value)
        assertEquals(
            mapOf(
                LoanField.INCOME to "Enter a non-negative amount in euros.",
                LoanField.DEBT to "Choose Yes or No.",
                LoanField.DEPOSIT to "Enter a non-negative amount in euros.",
                LoanField.TERM to "Enter a whole number from 1 to 40.",
                LoanField.PRICE to "Enter an asking price greater than zero.",
            ),
            emptyDraft.errors,
        )
    }
}

