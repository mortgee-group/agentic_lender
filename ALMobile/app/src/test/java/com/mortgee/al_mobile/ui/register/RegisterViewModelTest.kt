package com.mortgee.al_mobile.ui.register

import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.repository.AccountRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAccountRepository = FakeAccountRepository()
        viewModel = RegisterViewModel(fakeAccountRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no submit occurs on initialization`() {
        assertEquals(0, fakeAccountRepository.totalCalls)
        val state = viewModel.formState.value
        assertEquals("", state.displayName)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals(RegisterMode.CreateAccount, state.mode)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
    }

    @Test
    fun `updating displayName, email, password, and mode updates form state`() {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("SecretPass123!")

        val state = viewModel.formState.value
        assertEquals("Test Borrower", state.displayName)
        assertEquals("borrower@example.com", state.email)
        assertEquals("SecretPass123!", state.password)
        assertEquals(RegisterMode.CreateAccount, state.mode)
        assertTrue(state.canSubmit)

        viewModel.selectCompletionMode()
        assertEquals(RegisterMode.CompleteExisting, viewModel.formState.value.mode)

        viewModel.selectCreateAccountMode()
        assertEquals(RegisterMode.CreateAccount, viewModel.formState.value.mode)
    }

    @Test
    fun `convenience aliases onDisplayNameChanged, onEmailChanged, and onPasswordChanged update form state`() {
        viewModel.onDisplayNameChanged("Jane Borrower")
        viewModel.onEmailChanged("jane@example.com")
        viewModel.onPasswordChanged("MyPass456!")

        val state = viewModel.formState.value
        assertEquals("Jane Borrower", state.displayName)
        assertEquals("jane@example.com", state.email)
        assertEquals("MyPass456!", state.password)
    }

    @Test
    fun `submit with invalid or blank required fields marks field errors, preserves password, and makes zero repository calls`() {
        viewModel.updateDisplayName("   ")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("  RawPass123!  ")
        viewModel.submit()

        assertEquals(0, fakeAccountRepository.totalCalls)
        assertEquals(SignInResult.InvalidDisplayName, viewModel.formState.value.displayNameError)
        assertNull(viewModel.formState.value.emailError)
        assertNull(viewModel.formState.value.passwordError)
        assertEquals("  RawPass123!  ", viewModel.formState.value.password)

        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("")
        viewModel.submit()

        assertEquals(0, fakeAccountRepository.totalCalls)
        assertNull(viewModel.formState.value.displayNameError)
        assertEquals(SignInResult.InvalidEmail, viewModel.formState.value.emailError)
        assertEquals("  RawPass123!  ", viewModel.formState.value.password)

        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("")
        viewModel.submit()

        assertEquals(0, fakeAccountRepository.totalCalls)
        assertNull(viewModel.formState.value.emailError)
        assertEquals(SignInResult.WeakPassword, viewModel.formState.value.passwordError)
    }

    @Test
    fun `submit in CreateAccount mode captures fields, sets busy before launching, calls register, and clears password at completion`() = runTest {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("RawP@ssw0rd#2026")
        fakeAccountRepository.registerResult = SignInResult.Success

        viewModel.submit()

        // Synchronously set busy before launching coroutine work
        assertTrue(viewModel.formState.value.isSubmitting)
        assertNull(viewModel.formState.value.error)

        // Mutate state while coroutine is suspended to verify captured snapshot is used
        viewModel.updateDisplayName("Changed While In-Flight")
        viewModel.updateEmail("changed@example.com")
        viewModel.updatePassword("ChangedPassword")

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(0, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals(0, fakeAccountRepository.signInCallCount)
        assertEquals("Test Borrower", fakeAccountRepository.lastSubmittedDisplayName)
        assertEquals("borrower@example.com", fakeAccountRepository.lastSubmittedEmail)
        assertEquals("RawP@ssw0rd#2026", fakeAccountRepository.lastSubmittedPassword)

        // Cleanup resets isSubmitting and clears password
        val finalState = viewModel.formState.value
        assertEquals("", finalState.password)
        assertFalse(finalState.isSubmitting)
        assertNull(finalState.error)
    }

    @Test
    fun `submit in CompleteExisting mode calls completeRegistration and does not call register`() = runTest {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("ExactPass!2026")
        viewModel.selectCompletionMode()
        fakeAccountRepository.completeRegistrationResult = SignInResult.Success

        viewModel.submit()

        assertTrue(viewModel.formState.value.isSubmitting)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeAccountRepository.registerCallCount)
        assertEquals(1, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals(0, fakeAccountRepository.signInCallCount)
        assertEquals("Test Borrower", fakeAccountRepository.lastSubmittedDisplayName)
        assertEquals("borrower@example.com", fakeAccountRepository.lastSubmittedEmail)
        assertEquals("ExactPass!2026", fakeAccountRepository.lastSubmittedPassword)

        val finalState = viewModel.formState.value
        assertEquals("Test Borrower", finalState.displayName)
        assertEquals("borrower@example.com", finalState.email)
        assertEquals("", finalState.password)
        assertEquals(RegisterMode.CompleteExisting, finalState.mode)
        assertFalse(finalState.isSubmitting)
        assertNull(finalState.error)
    }

    @Test
    fun `two immediate submits trigger only one repository call`() = runTest {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("password123")
        fakeAccountRepository.registerResult = SignInResult.Success

        // Call submit twice in immediate succession
        viewModel.submit()
        viewModel.submit()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(0, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals(1, fakeAccountRepository.totalCalls)
    }

    @Test
    fun `failed submit preserves displayName and email, clears password, and allows retry`() = runTest {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("weak")
        fakeAccountRepository.registerResult = SignInResult.WeakPassword

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        val failedState = viewModel.formState.value
        assertEquals("Test Borrower", failedState.displayName)
        assertEquals("borrower@example.com", failedState.email)
        assertEquals("", failedState.password)
        assertFalse(failedState.isSubmitting)
        assertEquals(SignInResult.WeakPassword, failedState.error)
        assertEquals(SignInResult.WeakPassword, failedState.passwordError)
        assertEquals(RegisterMode.CreateAccount, failedState.mode)

        // Enter stronger password and retry in CreateAccount mode
        viewModel.updatePassword("StrongerPassword123!")
        assertNull(viewModel.formState.value.passwordError)
        fakeAccountRepository.registerResult = SignInResult.Success

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, fakeAccountRepository.registerCallCount)
        assertEquals(0, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals("StrongerPassword123!", fakeAccountRepository.lastSubmittedPassword)
        val successState = viewModel.formState.value
        assertEquals("Test Borrower", successState.displayName)
        assertEquals("borrower@example.com", successState.email)
        assertEquals("", successState.password)
        assertFalse(successState.isSubmitting)
        assertNull(successState.error)
    }

    @Test
    fun `failed register with InvalidDisplayName retains field error and routes retry through completeRegistration`() = runTest {
        viewModel.updateDisplayName("Rejected Name")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("ValidPassword123!")
        fakeAccountRepository.registerResult = SignInResult.InvalidDisplayName

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(0, fakeAccountRepository.completeRegistrationCallCount)

        val failedState = viewModel.formState.value
        assertEquals(SignInResult.InvalidDisplayName, failedState.displayNameError)
        assertEquals(RegisterMode.CompleteExisting, failedState.mode)
        assertEquals("", failedState.password)

        // Fix display name, re-enter password, and retry -> routes to completeRegistration
        viewModel.updateDisplayName("Corrected Borrower Name")
        viewModel.updatePassword("ValidPassword123!")
        fakeAccountRepository.completeRegistrationResult = SignInResult.Success

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(1, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals("Corrected Borrower Name", fakeAccountRepository.lastSubmittedDisplayName)
        assertNull(viewModel.formState.value.error)
        assertEquals("", viewModel.formState.value.password)
    }

    @Test
    fun `failed register with CompletionNeeded offers explicit completion and routes next submit to completeRegistration`() = runTest {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("ValidPassword123!")
        fakeAccountRepository.registerResult = SignInResult.CompletionNeeded

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        val failedState = viewModel.formState.value
        assertEquals(SignInResult.CompletionNeeded, failedState.formError)
        assertTrue(failedState.canOfferCompletion)
        assertEquals("", failedState.password)

        // User selects explicit Finish registration mode, re-enters password, and submits
        viewModel.selectCompletionMode()
        viewModel.updatePassword("ValidPassword123!")
        fakeAccountRepository.completeRegistrationResult = SignInResult.Success

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(1, fakeAccountRepository.completeRegistrationCallCount)
        assertNull(viewModel.formState.value.error)
        assertEquals("", viewModel.formState.value.password)
    }

    @Test
    fun `clearError and resetForm modify form state as expected`() = runTest {
        viewModel.updateDisplayName("Test Borrower")
        viewModel.updateEmail("test@example.com")
        viewModel.updatePassword("pass1234")
        fakeAccountRepository.registerResult = SignInResult.Connectivity

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SignInResult.Connectivity, viewModel.formState.value.error)

        viewModel.clearError()
        assertNull(viewModel.formState.value.error)

        viewModel.resetForm()
        assertEquals(RegisterFormState(), viewModel.formState.value)
    }

    private class FakeAccountRepository : AccountRepository {
        var signInCallCount = 0
        var registerCallCount = 0
        var completeRegistrationCallCount = 0
        val totalCalls: Int
            get() = signInCallCount + registerCallCount + completeRegistrationCallCount

        var lastSubmittedEmail: String? = null
        var lastSubmittedPassword: String? = null
        var lastSubmittedDisplayName: String? = null

        var signInResult: SignInResult = SignInResult.Success
        var registerResult: SignInResult = SignInResult.Success
        var completeRegistrationResult: SignInResult = SignInResult.Success

        override suspend fun signIn(email: String, password: String): SignInResult {
            signInCallCount++
            lastSubmittedEmail = email
            lastSubmittedPassword = password
            return signInResult
        }

        override suspend fun restoreSession(): SignInResult = signInResult

        override suspend fun register(
            email: String,
            password: String,
            displayName: String,
        ): SignInResult {
            registerCallCount++
            lastSubmittedEmail = email
            lastSubmittedPassword = password
            lastSubmittedDisplayName = displayName
            return registerResult
        }

        override suspend fun completeRegistration(
            email: String,
            password: String,
            displayName: String,
        ): SignInResult {
            completeRegistrationCallCount++
            lastSubmittedEmail = email
            lastSubmittedPassword = password
            lastSubmittedDisplayName = displayName
            return completeRegistrationResult
        }
    }
}
