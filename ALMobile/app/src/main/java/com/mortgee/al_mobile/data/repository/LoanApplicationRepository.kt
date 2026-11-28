package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.session.SessionKey

fun interface LoanApplicationRepository {
    suspend fun submit(key: SessionKey, request: CreateLoanRequest): LoanSubmitResult
}

sealed interface LoanSubmitResult {
    data class Created(val id: String) : LoanSubmitResult
    data class Invalid(val fields: Set<LoanField>, val other: Boolean) : LoanSubmitResult
    data object SignInRequired : LoanSubmitResult
    data object Forbidden : LoanSubmitResult
    data object NoBroker : LoanSubmitResult
    data object NotSent : LoanSubmitResult
    data object Unconfirmed : LoanSubmitResult
    data object Stale : LoanSubmitResult
}
