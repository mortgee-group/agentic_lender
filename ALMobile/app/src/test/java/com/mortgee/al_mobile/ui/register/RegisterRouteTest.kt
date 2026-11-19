package com.mortgee.al_mobile.ui.register

import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.repository.AccountRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [RegisterRoute] and [RegisterViewModel] state retention and
 * single-submission guarantees across configuration changes (e.g., screen rotation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterRouteTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: SuspendingFakeAccountRepository
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = SuspendingFakeAccountRepository()
        viewModel = RegisterViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rotation while submission is active retains in-flight submission and form state without starting another call`() = runTest {
        // 1. User enters fields on RegisterRoute
        viewModel.onDisplayNameChanged("Test Borrower")
        viewModel.onEmailChanged("borrower@example.com")
        viewModel.onPasswordChanged("Password123!")

        // 2. User triggers submit; repository suspends (simulating an in-flight network request)
        val pendingResult = CompletableDeferred<SignInResult>()
        fakeRepository.pendingRegisterResult = pendingResult

        viewModel.submit()
        testDispatcher.scheduler.runCurrent()

        // Verify submission is active and exactly one repository call has started
        assertTrue(viewModel.formState.value.isSubmitting)
        assertEquals(1, fakeRepository.registerCallCount)
        assertEquals(0, fakeRepository.completeRegistrationCallCount)

        // 3. Simulate device rotation: RegisterRoute recomposes and re-collects viewModel.formState
        val stateAfterRotation = viewModel.formState.value
        assertTrue(stateAfterRotation.isSubmitting)
        assertEquals("Test Borrower", stateAfterRotation.displayName)
        assertEquals("borrower@example.com", stateAfterRotation.email)
        assertEquals(RegisterMode.CreateAccount, stateAfterRotation.mode)

        // Even if a duplicate submit event is dispatched during/after rotation while still busy,
        // no second repository operation is started.
        viewModel.submit()
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCallCount)
        assertEquals(0, fakeRepository.completeRegistrationCallCount)

        // 4. Complete the in-flight repository call
        pendingResult.complete(SignInResult.Success)
        testDispatcher.scheduler.advanceUntilIdle()

        val completedState = viewModel.formState.value
        assertFalse(completedState.isSubmitting)
        assertEquals("Test Borrower", completedState.displayName)
        assertEquals("borrower@example.com", completedState.email)
        assertEquals("", completedState.password)
        assertNull(completedState.error)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    @Test
    fun `rotation in CompleteExisting mode retains active completion submission without starting another call`() = runTest {
        viewModel.selectCompletionMode()
        viewModel.onDisplayNameChanged("Existing Borrower")
        viewModel.onEmailChanged("existing@example.com")
        viewModel.onPasswordChanged("ExistingPass123!")

        val pendingResult = CompletableDeferred<SignInResult>()
        fakeRepository.pendingCompleteResult = pendingResult

        viewModel.submit()
        testDispatcher.scheduler.runCurrent()

        // Simulate rotation re-reading state from retained Hilt ViewModel
        val stateAfterRotation = viewModel.formState.value
        assertTrue(stateAfterRotation.isSubmitting)
        assertEquals(RegisterMode.CompleteExisting, stateAfterRotation.mode)
        assertEquals("Existing Borrower", stateAfterRotation.displayName)
        assertEquals("existing@example.com", stateAfterRotation.email)
        assertEquals(0, fakeRepository.registerCallCount)
        assertEquals(1, fakeRepository.completeRegistrationCallCount)

        // Duplicate submit during rotation is ignored
        viewModel.submit()
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.completeRegistrationCallCount)

        // Finish in-flight completion
        pendingResult.complete(SignInResult.Success)
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.formState.value
        assertFalse(finalState.isSubmitting)
        assertEquals("", finalState.password)
        assertEquals(RegisterMode.CompleteExisting, finalState.mode)
        assertEquals(1, fakeRepository.completeRegistrationCallCount)
    }

    private class SuspendingFakeAccountRepository : AccountRepository {
        var registerCallCount = 0
        var completeRegistrationCallCount = 0
        var pendingRegisterResult: CompletableDeferred<SignInResult>? = null
        var pendingCompleteResult: CompletableDeferred<SignInResult>? = null

        override suspend fun signIn(email: String, password: String): SignInResult {
            return SignInResult.Success
        }

        override suspend fun restoreSession(): SignInResult {
            return SignInResult.Success
        }

        override suspend fun register(
            email: String,
            password: String,
            displayName: String,
        ): SignInResult {
            registerCallCount++
            return pendingRegisterResult?.await() ?: SignInResult.Success
        }

        override suspend fun completeRegistration(
            email: String,
            password: String,
            displayName: String,
        ): SignInResult {
            completeRegistrationCallCount++
            return pendingCompleteResult?.await() ?: SignInResult.Success
        }
    }
}
