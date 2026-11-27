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

    @Test
    fun httpOutcomes() {
        assertEquals(
            LoanSubmitResult.SignInRequired,
            classifyLoanRejection(401, """{"detail":"Invalid or expired authentication token."}""", moshi),
        )
        assertEquals(
            LoanSubmitResult.SignInRequired,
            classifyLoanRejection(401, null, moshi),
        )

        assertEquals(
            LoanSubmitResult.Forbidden,
            classifyLoanRejection(403, """{"detail":"User profile not found."}""", moshi),
        )
        assertEquals(
            LoanSubmitResult.Forbidden,
            classifyLoanRejection(403, """{"detail":"Insufficient permissions. Required role: borrower"}""", moshi),
        )

        assertEquals(
            LoanSubmitResult.Invalid(fields = setOf(LoanField.DEPOSIT), other = false),
            classifyLoanRejection(
                422,
                """{"detail":[{"loc":["body","deposit"],"msg":"Input should be greater than or equal to 0"}]}""",
                moshi,
            ),
        )

        assertEquals(
            LoanSubmitResult.NoBroker,
            classifyLoanRejection(
                503,
                """{"detail":"No brokers are currently available"}""",
                moshi,
            ),
        )

        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(503, """{"detail":"upstream timeout"}""", moshi),
        )
        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(503, "<html>Service Unavailable</html>", moshi),
        )
        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(503, null, moshi),
        )
        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(503, """{"detail":["No brokers are currently available"]}""", moshi),
        )
        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(502, """{"detail":"Bad Gateway"}""", moshi),
        )
        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(307, null, moshi),
        )
        assertEquals(
            LoanSubmitResult.Unconfirmed,
            classifyLoanRejection(500, """{"detail":"Internal Server Error"}""", moshi),
        )
    }
}
