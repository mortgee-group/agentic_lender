package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.network.LoanApplicationApi
import com.mortgee.al_mobile.data.network.PrivateTokenException
import com.mortgee.al_mobile.data.network.PrivateTokenSeam
import com.mortgee.al_mobile.data.network.model.CreateLoanReceipt
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.network.model.LoanPropertyDto
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.di.NetworkModule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiLoanApplicationRepositoryTest {

    private val moshi = NetworkModule.provideMoshi()
    private val key = SessionKey("A", 7)
    private val validRequest = CreateLoanRequest(
        householdAnnualGrossIncome = 75000.0,
        existingDebtLiabilities = 10000.0,
        deposit = 70000.0,
        requestedMortgageTerm = 30,
        sellerAskingPrice = 350000.0,
        property = LoanPropertyDto(
            county = "Kilkenny",
            floorArea = 115.0,
            bedrooms = 3,
            bathrooms = 2,
            yearBuilt = 2005,
            propertyType = "SEMI_DETACHED",
            berRating = "B2",
        ),
    )

    @Test
    fun created201WithApplicationId() = runTest {
        val seam = RecordingPrivateTokenSeam()
        var recordedBearer: String? = null
        var recordedRequest: CreateLoanRequest? = null
        var callCount = 0

        val api = LoanApplicationApi { bearer, req ->
            callCount++
            recordedBearer = bearer
            recordedRequest = req
            Response.success(201, CreateLoanReceipt(applicationId = "application-001", message = "Created"))
        }

        val repository = ApiLoanApplicationRepository(api, seam, moshi)
        val outcome = repository.submit(key, validRequest)

        assertEquals(LoanSubmitResult.Created("application-001"), outcome)
        assertEquals(key, seam.recordedKey)
        assertEquals(true, seam.recordedIsMutation)
        assertEquals(1, seam.executeCount)
        assertEquals(1, callCount)
        assertEquals("Bearer unit-token", recordedBearer)
        assertEquals(validRequest, recordedRequest)
    }

    @Test
    fun http401ReachesSeamAsHttpExceptionBeforeClassification() = runTest {
        val seam = RecordingPrivateTokenSeam()
        var callCount = 0

        val api = LoanApplicationApi { _, _ ->
            callCount++
            errorResponse(401, """{"detail":"Invalid or expired authentication token."}""")
        }

        val repository = ApiLoanApplicationRepository(api, seam, moshi)
        val outcome = repository.submit(key, validRequest)

        assertEquals(LoanSubmitResult.SignInRequired, outcome)
        assertEquals(1, callCount)
        assertEquals(key, seam.recordedKey)
        assertEquals(true, seam.recordedIsMutation)
        val thrownToSeam = seam.caughtBlockException
        assertNotNull(thrownToSeam)
        assertTrue(thrownToSeam is HttpException)
        assertEquals(401, (thrownToSeam as HttpException).code())
    }

    @Test
    fun emptyId200WithIdAndIoExceptionAfterDispatchYieldUnconfirmed() = runTest {
        val seam1 = RecordingPrivateTokenSeam()
        val blankIdRepo = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                Response.success(201, CreateLoanReceipt(applicationId = "   "))
            },
            seam = seam1,
            moshi = moshi,
        )
        assertEquals(LoanSubmitResult.Unconfirmed, blankIdRepo.submit(key, validRequest))

        val seam2 = RecordingPrivateTokenSeam()
        val code200Repo = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                Response.success(200, CreateLoanReceipt(applicationId = "application-001"))
            },
            seam = seam2,
            moshi = moshi,
        )
        assertEquals(LoanSubmitResult.Unconfirmed, code200Repo.submit(key, validRequest))

        val seam3 = RecordingPrivateTokenSeam()
        val ioExceptionRepo = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                throw IOException("Socket closed after dispatch")
            },
            seam = seam3,
            moshi = moshi,
        )
        assertEquals(LoanSubmitResult.Unconfirmed, ioExceptionRepo.submit(key, validRequest))
    }

    @Test
    fun preflightFailuresSkipHttpDispatch() = runTest {
        var callCount = 0
        val api = LoanApplicationApi { _, _ ->
            callCount++
            Response.success(201, CreateLoanReceipt(applicationId = "application-001"))
        }

        val tokenFailedSeam = RecordingPrivateTokenSeam(
            preflightFailure = PrivateTokenException.TokenFetchFailed(IOException("Token fetch error")),
        )
        val tokenFailedRepo = ApiLoanApplicationRepository(api, tokenFailedSeam, moshi)
        assertEquals(LoanSubmitResult.NotSent, tokenFailedRepo.submit(key, validRequest))
        assertEquals(0, callCount)

        val staleSeam = RecordingPrivateTokenSeam(
            preflightFailure = PrivateTokenException.StaleSession(capturedKey = key, currentKey = SessionKey("A", 8)),
        )
        val staleRepo = ApiLoanApplicationRepository(api, staleSeam, moshi)
        assertEquals(LoanSubmitResult.Stale, staleRepo.submit(key, validRequest))
        assertEquals(0, callCount)

        val noSessionSeam = RecordingPrivateTokenSeam(
            preflightFailure = PrivateTokenException.NoActiveSession(),
        )
        val noSessionRepo = ApiLoanApplicationRepository(api, noSessionSeam, moshi)
        assertEquals(LoanSubmitResult.Stale, noSessionRepo.submit(key, validRequest))
        assertEquals(0, callCount)
    }

    @Test
    fun httpRejectionsUseClassifier() = runTest {
        val repo403 = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                errorResponse(403, """{"detail":"Insufficient permissions. Required role: borrower"}""")
            },
            seam = RecordingPrivateTokenSeam(),
            moshi = moshi,
        )
        assertEquals(LoanSubmitResult.Forbidden, repo403.submit(key, validRequest))

        val repo422 = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                errorResponse(
                    422,
                    """{"detail":[{"loc":["body","deposit"],"msg":"Input should be greater than or equal to 0"}]}""",
                )
            },
            seam = RecordingPrivateTokenSeam(),
            moshi = moshi,
        )
        assertEquals(
            LoanSubmitResult.Invalid(fields = setOf(LoanField.DEPOSIT), other = false),
            repo422.submit(key, validRequest),
        )

        val repo503NoBroker = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                errorResponse(503, """{"detail":"No brokers are currently available"}""")
            },
            seam = RecordingPrivateTokenSeam(),
            moshi = moshi,
        )
        assertEquals(LoanSubmitResult.NoBroker, repo503NoBroker.submit(key, validRequest))

        val repo503Gateway = ApiLoanApplicationRepository(
            api = LoanApplicationApi { _, _ ->
                errorResponse(503, """{"detail":"upstream timeout"}""")
            },
            seam = RecordingPrivateTokenSeam(),
            moshi = moshi,
        )
        assertEquals(LoanSubmitResult.Unconfirmed, repo503Gateway.submit(key, validRequest))
    }

    @Test
    fun cancellationEscapes() = runTest {
        val seam = RecordingPrivateTokenSeam()
        val api = LoanApplicationApi { _, _ ->
            throw CancellationException("Submit coroutine cancelled")
        }

        val repository = ApiLoanApplicationRepository(api, seam, moshi)
        try {
            repository.submit(key, validRequest)
            fail("Expected CancellationException to be rethrown")
        } catch (expected: CancellationException) {
            assertEquals("Submit coroutine cancelled", expected.message)
        }
    }

    private fun errorResponse(code: Int, json: String): Response<CreateLoanReceipt> {
        val body = json.toResponseBody("application/json".toMediaTypeOrNull())
        return Response.error(code, body)
    }

    private class RecordingPrivateTokenSeam(
        private val preflightFailure: Throwable? = null,
    ) : PrivateTokenSeam {
        var recordedKey: SessionKey? = null
        var recordedIsMutation: Boolean? = null
        var executeCount: Int = 0
        var caughtBlockException: Throwable? = null

        override suspend fun <T> executePrivate(
            entryKey: SessionKey?,
            isMutation: Boolean,
            block: suspend (token: String) -> T,
        ): Result<T> {
            executeCount++
            recordedKey = entryKey
            recordedIsMutation = isMutation

            preflightFailure?.let { failure ->
                if (failure is CancellationException) throw failure
                return Result.failure(failure)
            }

            return try {
                Result.success(block("unit-token"))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                caughtBlockException = t
                Result.failure(t)
            }
        }

        override suspend fun getToken(entryKey: SessionKey?, forceRefresh: Boolean): Result<String> =
            error("unused")

        override fun isAllowedOrigin(url: HttpUrl): Boolean =
            error("unused")

        override fun isAllowedOrigin(urlString: String): Boolean =
            error("unused")
    }
}
