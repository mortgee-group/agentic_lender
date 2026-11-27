package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.network.model.CreateLoanReceipt
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

fun interface LoanApplicationApi {
    @POST("applications")
    suspend fun create(
        @Header("Authorization") bearer: String,
        @Body request: CreateLoanRequest,
    ): Response<CreateLoanReceipt>
}
