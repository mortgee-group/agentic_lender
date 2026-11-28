package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.repository.LoanSubmitResult
import com.mortgee.al_mobile.data.session.SessionKey

private const val SERVER_FIELD_ERROR = "Review this value."
private const val SERVER_OTHER_NOTICE = "Review your application details."
private const val FORBIDDEN_NOTICE = "Borrower access is required."
private const val SIGN_IN_REQUIRED_NOTICE = "Please sign in again."
private const val NO_BROKER_NOTICE = "No broker is available. Try again later."
private const val NOT_SENT_NOTICE = "Your application was not sent. Try again."

enum class LoanPhase {
    CLOSED,
    EDITING,
    SENDING,
    RECEIPT,
    UNCONFIRMED,
    DENIED,
}

data class LoanUiState(
    val owner: SessionKey? = null,
    val phase: LoanPhase = LoanPhase.CLOSED,
    val draft: LoanDraft = LoanDraft(),
    val touched: Set<LoanField> = emptySet(),
    val serverFields: Set<LoanField> = emptySet(),
    val serverOther: Boolean = false,
    val notice: String? = null,
    val receiptId: String? = null,
) {
    val visibleErrors: Map<LoanField, String>
        get() {
            val localErrors = prepareLoan(draft).errors.filterKeys { it in touched }
            val serverErrors = serverFields.associateWith { SERVER_FIELD_ERROR }
            return serverErrors + localErrors
        }

    val canSubmit: Boolean
        get() = phase == LoanPhase.EDITING &&
            serverFields.isEmpty() &&
            !serverOther &&
            prepareLoan(draft).value != null
}

fun LoanUiState.afterResult(r: LoanSubmitResult): LoanUiState = when (r) {
    is LoanSubmitResult.Created -> LoanUiState(
        owner = owner,
        phase = LoanPhase.RECEIPT,
        receiptId = r.id,
    )
    LoanSubmitResult.Unconfirmed -> LoanUiState(
        owner = owner,
        phase = LoanPhase.UNCONFIRMED,
    )
    LoanSubmitResult.Forbidden -> LoanUiState(
        owner = owner,
        phase = LoanPhase.DENIED,
        notice = FORBIDDEN_NOTICE,
    )
    LoanSubmitResult.SignInRequired -> LoanUiState(
        owner = owner,
        phase = LoanPhase.DENIED,
        notice = SIGN_IN_REQUIRED_NOTICE,
    )
    LoanSubmitResult.Stale -> LoanUiState()
    is LoanSubmitResult.Invalid -> copy(
        phase = LoanPhase.EDITING,
        serverFields = r.fields,
        serverOther = r.other,
        notice = if (r.other) SERVER_OTHER_NOTICE else null,
        receiptId = null,
    )
    LoanSubmitResult.NoBroker -> copy(
        phase = LoanPhase.EDITING,
        serverFields = emptySet(),
        serverOther = false,
        notice = NO_BROKER_NOTICE,
        receiptId = null,
    )
    LoanSubmitResult.NotSent -> copy(
        phase = LoanPhase.EDITING,
        serverFields = emptySet(),
        serverOther = false,
        notice = NOT_SENT_NOTICE,
        receiptId = null,
    )
}
