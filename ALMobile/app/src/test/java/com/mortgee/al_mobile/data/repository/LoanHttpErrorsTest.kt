package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanHttpErrorsTest {

    private val moshi = NetworkModule.provideMoshi()

    @Test
    fun validationLocations() {
        val mixedBody = """
            {
              "detail": [
                {"loc": ["body", "property", "bathrooms"]},
                {"loc": ["body", "requestedMortgageTerm"]},
                {"loc": ["query", "deposit"]}
              ]
            }
        """.trimIndent()

        assertEquals(
            LoanSubmitResult.Invalid(
                fields = setOf(LoanField.BATHS, LoanField.TERM),
                other = true,
            ),
            decodeLoanValidation(moshi, mixedBody),
        )

        val rootDepositBody = """
            {
              "detail": [
                {
                  "loc": ["body"],
                  "type": "value_error",
                  "msg": "Value error, Deposit must be less than the property asking price"
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            LoanSubmitResult.Invalid(
                fields = setOf(LoanField.DEPOSIT),
                other = false,
            ),
            decodeLoanValidation(moshi, rootDepositBody),
        )

        val allTwelveFieldsBody = """
            {
              "detail": [
                {"loc": ["body", "householdAnnualGrossIncome"]},
                {"loc": ["body", "existingDebtLiabilities"]},
                {"loc": ["body", "deposit"]},
                {"loc": ["body", "requestedMortgageTerm"]},
                {"loc": ["body", "sellerAskingPrice"]},
                {"loc": ["body", "property", "county"]},
                {"loc": ["body", "property", "floor_area"]},
                {"loc": ["body", "property", "bedrooms"]},
                {"loc": ["body", "property", "bathrooms"]},
                {"loc": ["body", "property", "year_built"]},
                {"loc": ["body", "property", "property_type"]},
                {"loc": ["body", "property", "ber_rating"]}
              ]
            }
        """.trimIndent()

        assertEquals(
            LoanSubmitResult.Invalid(
                fields = LoanField.entries.toSet(),
                other = false,
            ),
            decodeLoanValidation(moshi, allTwelveFieldsBody),
        )
    }

    @Test
    fun malformedValidation() {
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(moshi, null),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(moshi, ""),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(moshi, "<html>502 Bad Gateway</html>"),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(moshi, "{}"),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(moshi, """{"detail": []}"""),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(moshi, """{"detail": "not-a-list"}"""),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(
                moshi,
                """{"detail": [{"loc": ["body"], "type": "value_error", "msg": "Value error, Some other root error"}]}""",
            ),
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = emptySet(), other = true),
            decodeLoanValidation(
                moshi,
                """{"detail": [{"loc": ["body", "property.floor_area"]}]}""",
            ),
        )
    }
}
