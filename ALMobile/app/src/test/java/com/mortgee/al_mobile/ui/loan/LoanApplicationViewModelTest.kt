package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.repository.LoanApplicationRepository
import com.mortgee.al_mobile.data.repository.LoanSubmitResult
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoanApplicationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sessionOwnsDraft() = runTest(testDispatcher) {
        val borrowerA1 = SessionKey(uid = "borrower-a", generation = 1L)
        val brokerB2 = SessionKey(uid = "broker-b", generation = 2L)
        val borrowerA3 = SessionKey(uid = "borrower-a", generation = 3L)

        var submitCalls = 0
        val repo = LoanApplicationRepository { _, _ ->
            submitCalls += 1
            LoanSubmitResult.Created("application-001")
        }

        val sessions = MutableStateFlow<SessionState>(
            authenticatedSession(borrowerA1, BackendRole.BORROWER),
        )
        val viewModel = LoanApplicationViewModel(repo = repo, sessions = sessions)
        runCurrent()

        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)

        // Active borrower A/1 opens a fresh EDITING draft
        viewModel.open(borrowerA1)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA1, viewModel.state.value.owner)
        val initialDraft = viewModel.state.value.draft

        // Opening the same open key twice retains the draft
        viewModel.open(borrowerA1)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA1, viewModel.state.value.owner)
        assertEquals(initialDraft, viewModel.state.value.draft)

        // Other keys cannot open while A/1 is current
        viewModel.open(brokerB2)
        viewModel.open(borrowerA3)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA1, viewModel.state.value.owner)

        // Broker B/2 session clears A/1's draft and cannot open A/1 or B/2
        sessions.value = authenticatedSession(brokerB2, BackendRole.BROKER)
        runCurrent()
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)

        viewModel.open(borrowerA1)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        viewModel.open(brokerB2)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)

        // Signed-out session cannot open A/1
        sessions.value = SessionState.SignedOut
        runCurrent()
        viewModel.open(borrowerA1)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)

        // Restore A/1, open draft, then switch to same-UID new generation A/3 -> clears A/1
        sessions.value = authenticatedSession(borrowerA1, BackendRole.BORROWER)
        runCurrent()
        viewModel.open(borrowerA1)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA1, viewModel.state.value.owner)

        sessions.value = authenticatedSession(borrowerA3, BackendRole.BORROWER)
        runCurrent()
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)

        // Old key A/1 cannot open while A/3 is current
        viewModel.open(borrowerA1)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)

        // Current key A/3 opens fresh draft
        viewModel.open(borrowerA3)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)

        // close(A/1) cannot close A/3's form
        viewModel.close(borrowerA1)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)

        // close(A/3) clears A/3's state
        viewModel.close(borrowerA3)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)

        assertEquals(0, submitCalls)
    }

    @Test
    fun changeOnlyCurrentDraft() = runTest(testDispatcher) {
        val borrowerA1 = SessionKey(uid = "borrower-a", generation = 1L)
        val borrowerA3 = SessionKey(uid = "borrower-a", generation = 3L)

        var submitCalls = 0
        val repo = LoanApplicationRepository { _, _ ->
            submitCalls += 1
            LoanSubmitResult.Created("application-001")
        }

        val sessions = MutableStateFlow<SessionState>(
            authenticatedSession(borrowerA3, BackendRole.BORROWER),
        )
        val viewModel = LoanApplicationViewModel(repo = repo, sessions = sessions)
        runCurrent()

        viewModel.open(borrowerA3)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)

        // DEBT edit is ignored before chooseDebt(true)
        viewModel.edit(borrowerA3, LoanField.DEBT, "10000")
        assertEquals("", viewModel.state.value.draft[LoanField.DEBT])
        assertFalse(LoanField.DEBT in viewModel.state.value.touched)

        // Choose Yes -> DEBT is touched and visible, needs a value
        viewModel.chooseDebt(borrowerA3, true)
        assertEquals(true, viewModel.state.value.draft.hasDebt)
        assertTrue(LoanField.DEBT in viewModel.state.value.touched)
        assertTrue(LoanField.DEBT in viewModel.state.value.visibleErrors)

        // Type "10000" -> valid debt clears visible DEBT error
        viewModel.edit(borrowerA3, LoanField.DEBT, "10000")
        assertEquals("10000", viewModel.state.value.draft[LoanField.DEBT])
        assertFalse(LoanField.DEBT in viewModel.state.value.visibleErrors)

        // Choose No -> hidden debt is erased and error cleared
        viewModel.chooseDebt(borrowerA3, false)
        assertEquals(false, viewModel.state.value.draft.hasDebt)
        assertEquals("", viewModel.state.value.draft[LoanField.DEBT])
        assertFalse(LoanField.DEBT in viewModel.state.value.visibleErrors)

        // Set unrelated field with error and valid values to verify preservation
        viewModel.edit(borrowerA3, LoanField.INCOME, "75000")
        viewModel.edit(borrowerA3, LoanField.YEAR, "1700")
        assertEquals("75000", viewModel.state.value.draft[LoanField.INCOME])
        assertEquals("1700", viewModel.state.value.draft[LoanField.YEAR])
        assertTrue(LoanField.YEAR in viewModel.state.value.visibleErrors)

        // Set price "350000", deposit "349999" -> both valid; change price to "349999" -> visible DEPOSIT error
        viewModel.edit(borrowerA3, LoanField.PRICE, "350000")
        viewModel.edit(borrowerA3, LoanField.DEPOSIT, "349999")
        assertFalse(LoanField.PRICE in viewModel.state.value.visibleErrors)
        assertFalse(LoanField.DEPOSIT in viewModel.state.value.visibleErrors)

        viewModel.edit(borrowerA3, LoanField.PRICE, "349999")
        assertEquals("349999", viewModel.state.value.draft[LoanField.PRICE])
        assertEquals("349999", viewModel.state.value.draft[LoanField.DEPOSIT])
        assertTrue(LoanField.DEPOSIT in viewModel.state.value.visibleErrors)
        // Unrelated field errors and raw inputs are preserved
        assertEquals("75000", viewModel.state.value.draft[LoanField.INCOME])
        assertEquals("1700", viewModel.state.value.draft[LoanField.YEAR])
        assertTrue(LoanField.YEAR in viewModel.state.value.visibleErrors)

        // EDITING under A/3 ignores edit(A/1, ...) and chooseDebt(A/1, ...)
        val snapshotUnderA3 = viewModel.state.value
        viewModel.edit(borrowerA1, LoanField.INCOME, "99999")
        viewModel.chooseDebt(borrowerA1, true)
        assertEquals(snapshotUnderA3, viewModel.state.value)

        // An edit or debt choice after close leaves CLOSED
        viewModel.close(borrowerA3)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        viewModel.edit(borrowerA3, LoanField.INCOME, "80000")
        viewModel.chooseDebt(borrowerA3, true)
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)
        assertNull(viewModel.state.value.owner)
        assertEquals(LoanDraft(), viewModel.state.value.draft)

        assertEquals(0, submitCalls)
    }

    @Test
    fun guardDoubleTapsAndLateSubmissionResults() = runTest(testDispatcher) {
        val borrowerA2 = SessionKey(uid = "borrower-a", generation = 2L)
        val borrowerA3 = SessionKey(uid = "borrower-a", generation = 3L)

        val pendingOutcomes = ArrayDeque<CompletableDeferred<LoanSubmitResult>>()
        val capturedCalls = mutableListOf<Pair<SessionKey, CreateLoanRequest>>()
        val repo = LoanApplicationRepository { key, request ->
            val deferred = CompletableDeferred<LoanSubmitResult>()
            capturedCalls += key to request
            pendingOutcomes.addLast(deferred)
            deferred.await()
        }

        val sessions = MutableStateFlow<SessionState>(
            authenticatedSession(borrowerA2, BackendRole.BORROWER),
        )
        val viewModel = LoanApplicationViewModel(repo = repo, sessions = sessions)
        runCurrent()

        viewModel.open(borrowerA2)

        // Invalid or incomplete draft -> zero calls
        viewModel.submit(borrowerA2)
        runCurrent()
        assertEquals(0, capturedCalls.size)
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)

        // Populate complete valid draft for A/2
        fillValidDraft(viewModel, borrowerA2)
        assertTrue(viewModel.state.value.canSubmit)

        // Two taps on a complete A/2 draft during one suspended call -> one request
        viewModel.submit(borrowerA2)
        viewModel.submit(borrowerA2)
        runCurrent()

        assertEquals(1, capturedCalls.size)
        assertEquals(borrowerA2, capturedCalls.single().first)
        assertEquals(LoanPhase.SENDING, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.canSubmit)

        // UI close while SENDING is guarded
        viewModel.close(borrowerA2)
        assertEquals(LoanPhase.SENDING, viewModel.state.value.phase)

        // Switch to same-UID new generation A/3, open and edit a fresh draft
        val firstAttempt = pendingOutcomes.removeFirst()
        sessions.value = authenticatedSession(borrowerA3, BackendRole.BORROWER)
        runCurrent()
        assertEquals(LoanPhase.CLOSED, viewModel.state.value.phase)

        viewModel.open(borrowerA3)
        viewModel.edit(borrowerA3, LoanField.INCOME, "82000")
        val a3DraftBeforeCompletion = viewModel.state.value.draft

        // Complete A/2 with Created("old-id") -> A/3 keeps its draft and has no receipt/error
        firstAttempt.complete(LoanSubmitResult.Created("old-id"))
        runCurrent()
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)
        assertEquals(a3DraftBeforeCompletion, viewModel.state.value.draft)
        assertNull(viewModel.state.value.receiptId)
        assertTrue(viewModel.state.value.serverFields.isEmpty())

        // Repeat stale completion check when A/2 completes with Invalid({YEAR}, false)
        sessions.value = authenticatedSession(borrowerA2, BackendRole.BORROWER)
        runCurrent()
        viewModel.open(borrowerA2)
        fillValidDraft(viewModel, borrowerA2)
        viewModel.submit(borrowerA2)
        runCurrent()
        assertEquals(2, capturedCalls.size)
        val secondAttempt = pendingOutcomes.removeFirst()

        sessions.value = authenticatedSession(borrowerA3, BackendRole.BORROWER)
        runCurrent()
        viewModel.open(borrowerA3)
        viewModel.edit(borrowerA3, LoanField.COUNTY, "Cork")
        val a3DraftBeforeInvalidCompletion = viewModel.state.value.draft

        secondAttempt.complete(
            LoanSubmitResult.Invalid(fields = setOf(LoanField.YEAR), other = false),
        )
        runCurrent()
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)
        assertEquals(a3DraftBeforeInvalidCompletion, viewModel.state.value.draft)
        assertNull(viewModel.state.value.receiptId)
        assertTrue(viewModel.state.value.serverFields.isEmpty())
        assertNull(viewModel.state.value.notice)

        // Complete valid draft under A/3: NoBroker returns to EDITING; only a new explicit tap sends a second call
        fillValidDraft(viewModel, borrowerA3)
        viewModel.submit(borrowerA3)
        runCurrent()
        assertEquals(3, capturedCalls.size)
        assertEquals(borrowerA3, capturedCalls.last().first)

        pendingOutcomes.removeFirst().complete(LoanSubmitResult.NoBroker)
        runCurrent()
        assertEquals(LoanPhase.EDITING, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)
        assertNotNull(viewModel.state.value.notice)
        assertTrue(viewModel.state.value.canSubmit)
        // No automatic retry occurred
        assertEquals(3, capturedCalls.size)

        // Explicit retry tap sends the second call for A/3 and transitions to RECEIPT on Created
        viewModel.submit(borrowerA3)
        runCurrent()
        assertEquals(4, capturedCalls.size)
        assertEquals(borrowerA3, capturedCalls.last().first)
        assertEquals(LoanPhase.SENDING, viewModel.state.value.phase)
        assertNull(viewModel.state.value.notice)

        pendingOutcomes.removeFirst().complete(LoanSubmitResult.Created("app-a3-001"))
        runCurrent()
        assertEquals(LoanPhase.RECEIPT, viewModel.state.value.phase)
        assertEquals(borrowerA3, viewModel.state.value.owner)
        assertEquals("app-a3-001", viewModel.state.value.receiptId)
    }

    private fun fillValidDraft(
        viewModel: LoanApplicationViewModel,
        key: SessionKey,
    ) {
        viewModel.edit(key, LoanField.INCOME, "75000")
        viewModel.chooseDebt(key, false)
        viewModel.edit(key, LoanField.DEPOSIT, "70000")
        viewModel.edit(key, LoanField.TERM, "30")
        viewModel.edit(key, LoanField.PRICE, "350000")
        viewModel.edit(key, LoanField.COUNTY, "co. Kilkenny")
        viewModel.edit(key, LoanField.AREA, "115")
        viewModel.edit(key, LoanField.BEDS, "3")
        viewModel.edit(key, LoanField.BATHS, "2")
        viewModel.edit(key, LoanField.YEAR, "2005")
        viewModel.edit(key, LoanField.TYPE, "SEMI_DETACHED")
        viewModel.edit(key, LoanField.BER, "B2")
    }

    private fun authenticatedSession(
        key: SessionKey,
        role: BackendRole,
    ): SessionState.Authenticated = SessionState.Authenticated(
        key = key,
        uid = key.uid,
        email = "${key.uid}@example.test",
        role = role,
    )
}
