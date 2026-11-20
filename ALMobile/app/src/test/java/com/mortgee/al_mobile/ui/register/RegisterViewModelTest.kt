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
        assertEquals("", state.firstName)
        assertEquals("", state.lastName)
        assertEquals("", state.phoneNumber)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals(RegisterMode.CreateAccount, state.mode)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
    }

    @Test
    fun `updating firstName, lastName, phoneNumber, email, password, and mode updates form state`() {
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
        viewModel.updatePhoneNumber("+353871234567")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("SecretPass123!")

        val state = viewModel.formState.value
        assertEquals("Test", state.firstName)
        assertEquals("Borrower", state.lastName)
        assertEquals("+353871234567", state.phoneNumber)
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
    fun `convenience aliases onFirstNameChanged, onLastNameChanged, onPhoneNumberChanged, onEmailChanged, and onPasswordChanged update form state`() {
        viewModel.onFirstNameChanged("Jane")
        viewModel.onLastNameChanged("Borrower")
        viewModel.onPhoneNumberChanged("+353870000000")
        viewModel.onEmailChanged("jane@example.com")
        viewModel.onPasswordChanged("MyPass456!")

        val state = viewModel.formState.value
        assertEquals("Jane", state.firstName)
        assertEquals("Borrower", state.lastName)
        assertEquals("+353870000000", state.phoneNumber)
        assertEquals("jane@example.com", state.email)
        assertEquals("MyPass456!", state.password)
    }

    @Test
    fun `submit with invalid or blank required fields marks field errors, preserves password, and makes zero repository calls`() {
        viewModel.updateFirstName("   ")
        viewModel.updateLastName("Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("  RawPass123!  ")
        viewModel.submit()

        assertEquals(0, fakeAccountRepository.totalCalls)
        assertEquals(SignInResult.InvalidFirstName, viewModel.formState.value.firstNameError)
        assertNull(viewModel.formState.value.lastNameError)
        assertNull(viewModel.formState.value.emailError)
        assertNull(viewModel.formState.value.passwordError)
        assertEquals("  RawPass123!  ", viewModel.formState.value.password)

        viewModel.updateFirstName("Test")
        viewModel.updateLastName("")
        viewModel.submit()

        assertEquals(0, fakeAccountRepository.totalCalls)
        assertNull(viewModel.formState.value.firstNameError)
        assertEquals(SignInResult.InvalidLastName, viewModel.formState.value.lastNameError)
        assertEquals("  RawPass123!  ", viewModel.formState.value.password)

        viewModel.updateLastName("Borrower")
        viewModel.updateEmail("")
        viewModel.submit()

        assertEquals(0, fakeAccountRepository.totalCalls)
        assertNull(viewModel.formState.value.lastNameError)
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
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
        viewModel.updatePhoneNumber("+353871234567")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("RawP@ssw0rd#2026")
        fakeAccountRepository.registerResult = SignInResult.Success

        viewModel.submit()

        // Synchronously set busy before launching coroutine work
        assertTrue(viewModel.formState.value.isSubmitting)
        assertNull(viewModel.formState.value.error)

        // Mutate state while coroutine is suspended to verify captured snapshot is used
        viewModel.updateFirstName("ChangedFirst")
        viewModel.updateLastName("ChangedLast")
        viewModel.updatePhoneNumber("+1000000")
        viewModel.updateEmail("changed@example.com")
        viewModel.updatePassword("ChangedPassword")

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(0, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals(0, fakeAccountRepository.signInCallCount)
        assertEquals("Test", fakeAccountRepository.lastSubmittedFirstName)
        assertEquals("Borrower", fakeAccountRepository.lastSubmittedLastName)
        assertEquals("+353871234567", fakeAccountRepository.lastSubmittedPhoneNumber)
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
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
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
        assertEquals("Test", fakeAccountRepository.lastSubmittedFirstName)
        assertEquals("Borrower", fakeAccountRepository.lastSubmittedLastName)
        assertNull(fakeAccountRepository.lastSubmittedPhoneNumber)
        assertEquals("borrower@example.com", fakeAccountRepository.lastSubmittedEmail)
        assertEquals("ExactPass!2026", fakeAccountRepository.lastSubmittedPassword)

        val finalState = viewModel.formState.value
        assertEquals("Test", finalState.firstName)
        assertEquals("Borrower", finalState.lastName)
        assertEquals("borrower@example.com", finalState.email)
        assertEquals("", finalState.password)
        assertEquals(RegisterMode.CompleteExisting, finalState.mode)
        assertFalse(finalState.isSubmitting)
        assertNull(finalState.error)
    }

    @Test
    fun `two immediate submits trigger only one repository call`() = runTest {
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
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
    fun `failed submit preserves profile fields and email, clears password, and allows retry`() = runTest {
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("weak")
        fakeAccountRepository.registerResult = SignInResult.WeakPassword

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        val failedState = viewModel.formState.value
        assertEquals("Test", failedState.firstName)
        assertEquals("Borrower", failedState.lastName)
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
        assertEquals("Test", successState.firstName)
        assertEquals("Borrower", successState.lastName)
        assertEquals("borrower@example.com", successState.email)
        assertEquals("", successState.password)
        assertFalse(successState.isSubmitting)
        assertNull(successState.error)
    }

    @Test
    fun `failed register with InvalidFirstName retains field error and routes retry through completeRegistration`() = runTest {
        viewModel.updateFirstName("Rejected")
        viewModel.updateLastName("Borrower")
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("ValidPassword123!")
        fakeAccountRepository.registerResult = SignInResult.InvalidFirstName

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(0, fakeAccountRepository.completeRegistrationCallCount)

        val failedState = viewModel.formState.value
        assertEquals(SignInResult.InvalidFirstName, failedState.firstNameError)
        assertEquals(RegisterMode.CompleteExisting, failedState.mode)
        assertEquals("", failedState.password)

        // Fix first name, re-enter password, and retry -> routes to completeRegistration
        viewModel.updateFirstName("Corrected")
        viewModel.updatePassword("ValidPassword123!")
        fakeAccountRepository.completeRegistrationResult = SignInResult.Success

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.registerCallCount)
        assertEquals(1, fakeAccountRepository.completeRegistrationCallCount)
        assertEquals("Corrected", fakeAccountRepository.lastSubmittedFirstName)
        assertNull(viewModel.formState.value.error)
        assertEquals("", viewModel.formState.value.password)
    }

    @Test
    fun `failed register with CompletionNeeded offers explicit completion and routes next submit to completeRegistration`() = runTest {
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
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
        viewModel.updateFirstName("Test")
        viewModel.updateLastName("Borrower")
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
        var lastSubmittedFirstName: String? = null
        var lastSubmittedLastName: String? = null
        var lastSubmittedPhoneNumber: String? = null

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
            firstName: String,
            lastName: String,
            phoneNumber: String?,
        ): SignInResult {
            registerCallCount++
            lastSubmittedEmail = email
            lastSubmittedPassword = password
            lastSubmittedFirstName = firstName
            lastSubmittedLastName = lastName
            lastSubmittedPhoneNumber = phoneNumber
            return registerResult
        }

        override suspend fun completeRegistration(
            email: String,
            password: String,
            firstName: String,
            lastName: String,
            phoneNumber: String?,
        ): SignInResult {
            completeRegistrationCallCount++
            lastSubmittedEmail = email
            lastSubmittedPassword = password
            lastSubmittedFirstName = firstName
            lastSubmittedLastName = lastName
            lastSubmittedPhoneNumber = phoneNumber
            return completeRegistrationResult
        }
    }
}
