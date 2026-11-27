package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.repository.LoanSubmitResult
import com.mortgee.al_mobile.data.session.SessionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanUiStateTest {

    private val ownerKey = SessionKey(uid = "borrower-1", generation = 7L)

    private fun validDraft(): LoanDraft = LoanDraft()
        .edit(LoanField.INCOME, "75000")
        .chooseDebt(true)
        .edit(LoanField.DEBT, "10000")
        .edit(LoanField.DEPOSIT, "70000")
        .edit(LoanField.TERM, "30")
        .edit(LoanField.PRICE, "350000")
        .edit(LoanField.COUNTY, "Kilkenny")
        .edit(LoanField.AREA, "115")
        .edit(LoanField.BEDS, "3")
        .edit(LoanField.BATHS, "2")
        .edit(LoanField.YEAR, "2005")
        .edit(LoanField.TYPE, "SEMI_DETACHED")
        .edit(LoanField.BER, "B2")

    @Test
    fun visibleErrorsMergesTouchedLocalAndServerErrorsWithLocalPrecedence() {
        val draft = validDraft()
            .edit(LoanField.YEAR, "1700")
            .edit(LoanField.TERM, "50")

        // Untouched local errors are hidden; server error on untouched field is shown;
        // on a touched field with both local and server error, local error wins.
        val state = LoanUiState(
            owner = ownerKey,
            phase = LoanPhase.EDITING,
            draft = draft,
            touched = setOf(LoanField.YEAR, LoanField.INCOME),
            serverFields = setOf(LoanField.YEAR, LoanField.COUNTY),
        )

        assertEquals(
            mapOf(
                LoanField.YEAR to "Enter a build year from 1800 to 2030.",
                LoanField.COUNTY to "Review this value.",
            ),
            state.visibleErrors,
        )
    }

    @Test
    fun canSubmitGatingRequiresEditingValidDraftAndNoServerFlags() {
        val valid = validDraft()
        val ready = LoanUiState(
            owner = ownerKey,
            phase = LoanPhase.EDITING,
            draft = valid,
        )
        assertTrue(ready.canSubmit)

        for (nonEditingPhase in listOf(
            LoanPhase.CLOSED,
            LoanPhase.SENDING,
            LoanPhase.RECEIPT,
            LoanPhase.UNCONFIRMED,
            LoanPhase.DENIED,
        )) {
            assertFalse(ready.copy(phase = nonEditingPhase).canSubmit)
        }

        assertFalse(ready.copy(draft = valid.edit(LoanField.PRICE, "0")).canSubmit)
        assertFalse(ready.copy(serverFields = setOf(LoanField.YEAR)).canSubmit)
        assertFalse(ready.copy(serverOther = true).canSubmit)
    }

    @Test
    fun afterResultReceiptAndTerminalStatesClearDraftAndDisableSubmit() {
        val sending = LoanUiState(
            owner = ownerKey,
            phase = LoanPhase.SENDING,
            draft = validDraft(),
            touched = LoanField.entries.toSet(),
        )

        val receipt = sending.afterResult(LoanSubmitResult.Created("application-001"))
        assertEquals(
            LoanUiState(
                owner = ownerKey,
                phase = LoanPhase.RECEIPT,
                draft = LoanDraft(),
                receiptId = "application-001",
            ),
            receipt,
        )
        assertFalse(receipt.canSubmit)

        val unconfirmed = sending.afterResult(LoanSubmitResult.Unconfirmed)
        assertEquals(
            LoanUiState(
                owner = ownerKey,
                phase = LoanPhase.UNCONFIRMED,
                draft = LoanDraft(),
            ),
            unconfirmed,
        )
        assertFalse(unconfirmed.canSubmit)

        val forbidden = sending.afterResult(LoanSubmitResult.Forbidden)
        assertEquals(
            LoanUiState(
                owner = ownerKey,
                phase = LoanPhase.DENIED,
                draft = LoanDraft(),
                notice = "Borrower access is required.",
            ),
            forbidden,
        )
        assertFalse(forbidden.canSubmit)

        val signInRequired = sending.afterResult(LoanSubmitResult.SignInRequired)
        assertEquals(
            LoanUiState(
                owner = ownerKey,
                phase = LoanPhase.DENIED,
                draft = LoanDraft(),
                notice = "Please sign in again.",
            ),
            signInRequired,
        )
        assertFalse(signInRequired.canSubmit)

        val stale = sending.afterResult(LoanSubmitResult.Stale)
        assertEquals(LoanUiState(), stale)
        assertFalse(stale.canSubmit)
    }

    @Test
    fun afterResultEditableFailuresRetainDraftAndSetExpectedFlagsAndNotices() {
        val draft = validDraft()
        val touched = setOf(LoanField.INCOME, LoanField.YEAR)
        val sending = LoanUiState(
            owner = ownerKey,
            phase = LoanPhase.SENDING,
            draft = draft,
            touched = touched,
        )

        val invalidFieldOnly = sending.afterResult(
            LoanSubmitResult.Invalid(
                fields = setOf(LoanField.YEAR),
                other = false,
            ),
        )
        assertEquals(LoanPhase.EDITING, invalidFieldOnly.phase)
        assertEquals(ownerKey, invalidFieldOnly.owner)
        assertEquals(draft, invalidFieldOnly.draft)
        assertEquals(touched, invalidFieldOnly.touched)
        assertEquals(setOf(LoanField.YEAR), invalidFieldOnly.serverFields)
        assertFalse(invalidFieldOnly.serverOther)
        assertNull(invalidFieldOnly.notice)
        assertEquals(
            mapOf(LoanField.YEAR to "Review this value."),
            invalidFieldOnly.visibleErrors,
        )
        assertFalse(invalidFieldOnly.canSubmit)

        val invalidWithOther = sending.afterResult(
            LoanSubmitResult.Invalid(
                fields = emptySet(),
                other = true,
            ),
        )
        assertEquals(LoanPhase.EDITING, invalidWithOther.phase)
        assertEquals(draft, invalidWithOther.draft)
        assertTrue(invalidWithOther.serverFields.isEmpty())
        assertTrue(invalidWithOther.serverOther)
        assertEquals("Review your application details.", invalidWithOther.notice)
        assertFalse(invalidWithOther.canSubmit)

        val noBroker = sending.afterResult(LoanSubmitResult.NoBroker)
        assertEquals(LoanPhase.EDITING, noBroker.phase)
        assertEquals(ownerKey, noBroker.owner)
        assertEquals(draft, noBroker.draft)
        assertEquals(touched, noBroker.touched)
        assertEquals("No broker is available. Try again later.", noBroker.notice)
        assertTrue(noBroker.canSubmit)

        val notSent = sending.afterResult(LoanSubmitResult.NotSent)
        assertEquals(LoanPhase.EDITING, notSent.phase)
        assertEquals(ownerKey, notSent.owner)
        assertEquals(draft, notSent.draft)
        assertEquals(touched, notSent.touched)
        assertEquals("Your application was not sent. Try again.", notSent.notice)
        assertTrue(notSent.canSubmit)
    }
}
