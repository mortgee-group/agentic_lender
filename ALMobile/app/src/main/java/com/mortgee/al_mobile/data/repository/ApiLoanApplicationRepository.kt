package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.network.LoanApplicationApi
import com.mortgee.al_mobile.data.network.PrivateTokenException
import com.mortgee.al_mobile.data.network.PrivateTokenSeam
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.session.SessionKey
import com.squareup.moshi.Moshi
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException

class ApiLoanApplicationRepository @Inject constructor(
    private val api: LoanApplicationApi,
    private val seam: PrivateTokenSeam,
    private val moshi: Moshi,
) : LoanApplicationRepository {

    override suspend fun submit(key: SessionKey, request: CreateLoanRequest): LoanSubmitResult {
        val result = seam.executePrivate(key, isMutation = true) { token ->
            val r = api.create("Bearer $token", request)
            if (!r.isSuccessful) throw HttpException(r)
            val id = r.body()?.applicationId?.takeIf { it.isNotBlank() }
            if (r.code() == 201 && id != null) LoanSubmitResult.Created(id) else LoanSubmitResult.Unconfirmed
        }

        return result.fold(
            onSuccess = { it },
            onFailure = { error ->
                when (error) {
                    is CancellationException -> throw error
                    is PrivateTokenException.StaleSession,
                    is PrivateTokenException.NoActiveSession,
                    -> LoanSubmitResult.Stale
                    is PrivateTokenException.TokenFetchFailed -> LoanSubmitResult.NotSent
                    is HttpException -> {
                        val body = try {
                            error.response()?.errorBody()?.use { it.string() }
                        } catch (_: IOException) {
                            null
                        }
                        classifyLoanRejection(error.code(), body, moshi)
                    }
                    else -> LoanSubmitResult.Unconfirmed
                }
            },
        )
    }
}
