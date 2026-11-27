package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.repository.LoanApplicationRepository
import com.mortgee.al_mobile.data.repository.LoanSubmitResult
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionState
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
import org.junit.Assert.assertNull
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
