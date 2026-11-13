package com.mortgee.al_mobile.ui.signin

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
class SignInViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var viewModel: SignInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAccountRepository = FakeAccountRepository()
        viewModel = SignInViewModel(fakeAccountRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no submit occurs on initialization`() {
        assertEquals(0, fakeAccountRepository.callCount)
        val state = viewModel.formState.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
    }

    @Test
    fun `updating email and password updates form state`() {
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("SecretPass123!")

        val state = viewModel.formState.value
        assertEquals("borrower@example.com", state.email)
        assertEquals("SecretPass123!", state.password)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `convenience aliases onEmailChanged and onPasswordChanged update form state`() {
        viewModel.onEmailChanged("broker@example.com")
        viewModel.onPasswordChanged("MyPass456!")

        val state = viewModel.formState.value
        assertEquals("broker@example.com", state.email)
        assertEquals("MyPass456!", state.password)
    }

    @Test
    fun `submit with invalid or blank required fields does not call repository`() {
        viewModel.submit()
        assertEquals(0, fakeAccountRepository.callCount)

        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("")
        viewModel.submit()
        assertEquals(0, fakeAccountRepository.callCount)

        viewModel.updateEmail("")
        viewModel.updatePassword("password")
        viewModel.submit()
        assertEquals(0, fakeAccountRepository.callCount)
    }

    @Test
    fun `submit snapshots credentials, sets busy before launching, passes password unchanged, and clears password in cleanup`() = runTest {
        viewModel.updateEmail("broker@example.com")
        viewModel.updatePassword("RawP@ssw0rd#2026")
        fakeAccountRepository.resultToReturn = SignInResult.Success

        viewModel.submit()

        // Synchronously set busy before launching coroutine work
        assertTrue(viewModel.formState.value.isSubmitting)
        assertNull(viewModel.formState.value.error)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.callCount)
        assertEquals("broker@example.com", fakeAccountRepository.lastSubmittedEmail)
        assertEquals("RawP@ssw0rd#2026", fakeAccountRepository.lastSubmittedPassword)

        // Cleanup resets isSubmitting and clears password while retaining email
        val finalState = viewModel.formState.value
        assertEquals("broker@example.com", finalState.email)
        assertEquals("", finalState.password)
        assertFalse(finalState.isSubmitting)
        assertNull(finalState.error)
    }

    @Test
    fun `two immediate submits trigger only one repository call`() = runTest {
        viewModel.updateEmail("underwriter@example.com")
        viewModel.updatePassword("password123")
        fakeAccountRepository.resultToReturn = SignInResult.Success

        // Call submit twice in immediate succession
        viewModel.submit()
        viewModel.submit()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.callCount)
    }

    @Test
    fun `failed submit sets error state, clears password, and allows retry`() = runTest {
        viewModel.updateEmail("borrower@example.com")
        viewModel.updatePassword("wrongPassword")
        fakeAccountRepository.resultToReturn = SignInResult.InvalidCredentials

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAccountRepository.callCount)
        val failedState = viewModel.formState.value
        assertEquals("borrower@example.com", failedState.email)
        assertEquals("", failedState.password)
        assertFalse(failedState.isSubmitting)
        assertEquals(SignInResult.InvalidCredentials, failedState.error)

        // Enter new password and retry
        viewModel.updatePassword("correctPassword")
        fakeAccountRepository.resultToReturn = SignInResult.Success

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, fakeAccountRepository.callCount)
        assertEquals("correctPassword", fakeAccountRepository.lastSubmittedPassword)
        val successState = viewModel.formState.value
        assertEquals("borrower@example.com", successState.email)
        assertEquals("", successState.password)
        assertFalse(successState.isSubmitting)
        assertNull(successState.error)
    }

    @Test
    fun `clearError and resetForm modify form state as expected`() = runTest {
        viewModel.updateEmail("test@example.com")
        viewModel.updatePassword("pass")
        fakeAccountRepository.resultToReturn = SignInResult.GenericFailure

        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SignInResult.GenericFailure, viewModel.formState.value.error)

        viewModel.clearError()
        assertNull(viewModel.formState.value.error)

        viewModel.resetForm()
        assertEquals(SignInFormState(), viewModel.formState.value)
    }

    private class FakeAccountRepository : AccountRepository {
        var callCount = 0
        var lastSubmittedEmail: String? = null
        var lastSubmittedPassword: String? = null
        var resultToReturn: SignInResult = SignInResult.Success

        override suspend fun signIn(email: String, password: String): SignInResult {
            callCount++
            lastSubmittedEmail = email
            lastSubmittedPassword = password
            return resultToReturn
        }

        override suspend fun restoreSession(): SignInResult = resultToReturn
    }
}
