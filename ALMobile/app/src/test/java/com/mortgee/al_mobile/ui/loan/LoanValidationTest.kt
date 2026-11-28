package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.network.model.LoanPropertyDto
import com.mortgee.al_mobile.di.NetworkModule
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

    @Test
    fun propertyRules() {
        val validStudio = LoanDraft()
            .edit(LoanField.COUNTY, "co. Kilkenny")
            .edit(LoanField.AREA, "115")
            .edit(LoanField.BEDS, "0")
            .edit(LoanField.BATHS, "0")
            .edit(LoanField.YEAR, "1800")
            .edit(LoanField.TYPE, "semi_detached")
            .edit(LoanField.BER, "exempt")

        val checkedStudio = validateProperty(validStudio)
        assertEquals(
            LoanPropertyDto(
                county = "co. Kilkenny",
                floorArea = 115.0,
                bedrooms = 0,
                bathrooms = 0,
                yearBuilt = 1800,
                propertyType = "SEMI_DETACHED",
                berRating = "EXEMPT",
            ),
            checkedStudio.value,
        )
        assertTrue(checkedStudio.errors.isEmpty())

        val year2030 = validateProperty(
            validStudio
                .edit(LoanField.BEDS, "3")
                .edit(LoanField.BATHS, "2")
                .edit(LoanField.YEAR, "2030")
                .edit(LoanField.BER, "B2"),
        )
        assertEquals(
            LoanPropertyDto(
                county = "co. Kilkenny",
                floorArea = 115.0,
                bedrooms = 3,
                bathrooms = 2,
                yearBuilt = 2030,
                propertyType = "SEMI_DETACHED",
                berRating = "B2",
            ),
            year2030.value,
        )
        assertTrue(year2030.errors.isEmpty())

        for (badCounty in listOf("", "Cork", "co. Atlantis", " co. Kilkenny ", "co. cork")) {
            val res = validateProperty(validStudio.edit(LoanField.COUNTY, badCounty))
            assertNull(res.value)
            assertEquals(
                mapOf(LoanField.COUNTY to "Select a county from the list."),
                res.errors,
            )
        }

        for (badYear in listOf("1799", "2031", "", "abc")) {
            val res = validateProperty(validStudio.edit(LoanField.YEAR, badYear))
            assertNull(res.value)
            assertEquals(
                mapOf(LoanField.YEAR to "Enter a build year from 1800 to 2030."),
                res.errors,
            )
        }

        for (badBaths in listOf("-1", "1.5", "", "two")) {
            val res = validateProperty(validStudio.edit(LoanField.BATHS, badBaths))
            assertNull(res.value)
            assertEquals(
                mapOf(LoanField.BATHS to "Enter zero or more whole bathrooms."),
                res.errors,
            )
        }

        val blankBer = validateProperty(validStudio.edit(LoanField.BER, "   "))
        assertNull(blankBer.value)
        assertEquals(
            mapOf(LoanField.BER to "Choose a BER rating."),
            blankBer.errors,
        )

        val unknownBer = validateProperty(validStudio.edit(LoanField.BER, "Z9"))
        assertNull(unknownBer.value)
        assertEquals(
            mapOf(LoanField.BER to "Unsupported BER rating code"),
            unknownBer.errors,
        )

        val unknownType = validateProperty(validStudio.edit(LoanField.TYPE, "CASTLE"))
        assertNull(unknownType.value)
        assertEquals(
            mapOf(LoanField.TYPE to "Unsupported property type code"),
            unknownType.errors,
        )

        val zeroArea = validateProperty(validStudio.edit(LoanField.AREA, "0"))
        assertNull(zeroArea.value)
        assertEquals(
            mapOf(LoanField.AREA to "Size must be a positive number in square metres"),
            zeroArea.errors,
        )

        val negativeBeds = validateProperty(validStudio.edit(LoanField.BEDS, "-1"))
        assertNull(negativeBeds.value)
        assertEquals(
            mapOf(LoanField.BEDS to "Bedrooms must be 0 (for Studio) or a positive whole number"),
            negativeBeds.errors,
        )

        val emptyProperty = validateProperty(LoanDraft())
        assertNull(emptyProperty.value)
        assertEquals(
            mapOf(
                LoanField.COUNTY to "Select a county from the list.",
                LoanField.AREA to "Property size is required",
                LoanField.BEDS to "Number of bedrooms is required",
                LoanField.BATHS to "Enter zero or more whole bathrooms.",
                LoanField.YEAR to "Enter a build year from 1800 to 2030.",
                LoanField.TYPE to "Property type is required",
                LoanField.BER to "Choose a BER rating.",
            ),
            emptyProperty.errors,
        )
    }

    @Test
    fun prepareRequest() {
        val completeWithDebt = LoanDraft()
            .edit(LoanField.INCOME, "75000")
            .chooseDebt(true)
            .edit(LoanField.DEBT, "10000")
            .edit(LoanField.DEPOSIT, "70000")
            .edit(LoanField.TERM, "30")
            .edit(LoanField.PRICE, "350000")
            .edit(LoanField.COUNTY, "co. Kilkenny")
            .edit(LoanField.AREA, "115")
            .edit(LoanField.BEDS, "3")
            .edit(LoanField.BATHS, "2")
            .edit(LoanField.YEAR, "2005")
            .edit(LoanField.TYPE, "SEMI_DETACHED")
            .edit(LoanField.BER, "B2")

        val expectedProperty = LoanPropertyDto(
            county = "co. Kilkenny",
            floorArea = 115.0,
            bedrooms = 3,
            bathrooms = 2,
            yearBuilt = 2005,
            propertyType = "SEMI_DETACHED",
            berRating = "B2",
        )

        val checkedWithDebt = prepareLoan(completeWithDebt)
        assertEquals(
            CreateLoanRequest(
                householdAnnualGrossIncome = 75000.0,
                existingDebtLiabilities = 10000.0,
                deposit = 70000.0,
                requestedMortgageTerm = 30,
                sellerAskingPrice = 350000.0,
                property = expectedProperty,
            ),
            checkedWithDebt.value,
        )
        assertTrue(checkedWithDebt.errors.isEmpty())

        val completeNoDebt = completeWithDebt.chooseDebt(false)
        val checkedNoDebt = prepareLoan(completeNoDebt)
        assertEquals(
            CreateLoanRequest(
                householdAnnualGrossIncome = 75000.0,
                existingDebtLiabilities = 0.0,
                deposit = 70000.0,
                requestedMortgageTerm = 30,
                sellerAskingPrice = 350000.0,
                property = expectedProperty,
            ),
            checkedNoDebt.value,
        )
        assertTrue(checkedNoDebt.errors.isEmpty())

        // Serialize the request produced by prepareLoan() for an otherwise valid draft with Cork selected
        val corkDraft = completeWithDebt.edit(LoanField.COUNTY, "co. Cork")
        val corkChecked = prepareLoan(corkDraft)
        assertTrue(corkChecked.errors.isEmpty())
        val corkRequest = requireNotNull(corkChecked.value)
        assertEquals("co. Cork", corkRequest.property.county)

        val moshi = NetworkModule.provideMoshi()
        val corkJson = moshi.adapter(CreateLoanRequest::class.java).toJson(corkRequest)
        val corkJsonMap = moshi.adapter(Map::class.java).fromJson(corkJson) as Map<*, *>
        val corkPropertyMap = corkJsonMap["property"] as Map<*, *>
        assertEquals("co. Cork", corkPropertyMap["county"])

        // Repeat with "", "Cork" and "co. Atlantis" and verify that no request can be built
        for (invalidCounty in listOf("", "Cork", "co. Atlantis")) {
            val invalidChecked = prepareLoan(completeWithDebt.edit(LoanField.COUNTY, invalidCounty))
            assertNull(invalidChecked.value)
            assertEquals(
                mapOf(LoanField.COUNTY to "Select a county from the list."),
                invalidChecked.errors,
            )
        }

        val fourAffordabilityAlone = LoanDraft()
            .edit(LoanField.INCOME, "75000")
            .chooseDebt(true)
            .edit(LoanField.DEBT, "10000")
            .edit(LoanField.DEPOSIT, "70000")
            .edit(LoanField.TERM, "30")
        val checkedFourOnly = prepareLoan(fourAffordabilityAlone)
        assertNull(checkedFourOnly.value)
        assertEquals(
            mapOf(
                LoanField.PRICE to "Enter an asking price greater than zero.",
                LoanField.COUNTY to "Select a county from the list.",
                LoanField.AREA to "Property size is required",
                LoanField.BEDS to "Number of bedrooms is required",
                LoanField.BATHS to "Enter zero or more whole bathrooms.",
                LoanField.YEAR to "Enter a build year from 1800 to 2030.",
                LoanField.TYPE to "Property type is required",
                LoanField.BER to "Choose a BER rating.",
            ),
            checkedFourOnly.errors,
        )

        val missingYearAndBadTerm = completeWithDebt
            .edit(LoanField.TERM, "41")
            .edit(LoanField.YEAR, "")
        val checkedBothSectionsInvalid = prepareLoan(missingYearAndBadTerm)
        assertNull(checkedBothSectionsInvalid.value)
        assertEquals(
            mapOf(
                LoanField.TERM to "Enter a whole number from 1 to 40.",
                LoanField.YEAR to "Enter a build year from 1800 to 2030.",
            ),
            checkedBothSectionsInvalid.errors,
        )

        val depositEqualsPrice = completeWithDebt.edit(LoanField.DEPOSIT, "350000")
        val checkedDepositEqualsPrice = prepareLoan(depositEqualsPrice)
        assertNull(checkedDepositEqualsPrice.value)
        assertEquals(
            mapOf(LoanField.DEPOSIT to "Deposit must be less than the asking price."),
            checkedDepositEqualsPrice.errors,
        )
    }
}


