package com.mortgee.al_mobile.ui.navigation

import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.repository.AccountRepository
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionState
import com.mortgee.al_mobile.ui.register.RegisterMode
import com.mortgee.al_mobile.ui.register.RegisterViewModel
import com.mortgee.al_mobile.ui.signin.formatErrorMessage
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
class AppNavigationTest {

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
    fun `unauthenticated states gate private content and default to SignIn guest destination`() {
        val signedOut = SessionState.SignedOut
        assertFalse(signedOut.isAuthenticated)
        assertNull(signedOut.sessionKey)

        val establishing = SessionState.Establishing("att_1")
        assertFalse(establishing.isAuthenticated)
        assertNull(establishing.sessionKey)

        val restoring = SessionState.Restoring("att_2")
        assertFalse(restoring.isAuthenticated)
        assertNull(restoring.sessionKey)

        val initialGuestNav = GuestNavState()
        assertEquals(GuestDestination.SignIn, initialGuestNav.destination)
        assertNull(initialGuestNav.destination.initialRegisterMode)
        assertEquals(0, initialGuestNav.registerEntryGeneration)
    }

    @Test
    fun `guest can enter registration in CreateAccount mode or explicit completion in CompleteExisting mode`() {
        val initial = GuestNavState()
        val fakeRepo = FakeAccountRepository()
        val registerViewModel = RegisterViewModel(fakeRepo)

        // 1. Guest enters standard Borrower registration from SignIn
        val inRegister = initial.enterRegister()
        assertEquals(GuestDestination.Register, inRegister.destination)
        assertEquals(RegisterMode.CreateAccount, inRegister.destination.initialRegisterMode)
        assertEquals(1, inRegister.registerEntryGeneration)

        registerViewModel.applyNavigationEntry(
            entryGeneration = inRegister.registerEntryGeneration,
            initialMode = inRegister.destination.initialRegisterMode!!,
        )
        assertEquals(RegisterMode.CreateAccount, registerViewModel.formState.value.mode)

        // 2. Guest returns to SignIn and enters explicit completion ("Finish registration")
        val backToSignIn = inRegister.returnToSignIn()
        assertEquals(GuestDestination.SignIn, backToSignIn.destination)
        assertNull(backToSignIn.destination.initialRegisterMode)

        val inCompleteRegistration = backToSignIn.enterCompleteRegistration()
        assertEquals(GuestDestination.CompleteRegistration, inCompleteRegistration.destination)
        assertEquals(RegisterMode.CompleteExisting, inCompleteRegistration.destination.initialRegisterMode)
        assertTrue(inCompleteRegistration.registerEntryGeneration > inRegister.registerEntryGeneration)

        registerViewModel.applyNavigationEntry(
            entryGeneration = inCompleteRegistration.registerEntryGeneration,
            initialMode = inCompleteRegistration.destination.initialRegisterMode!!,
        )
        assertEquals(RegisterMode.CompleteExisting, registerViewModel.formState.value.mode)
        assertTrue(registerViewModel.formState.value.isCompletionMode)
    }

    @Test
    fun `registration success reaches permitted AuthenticatedLanding through session state and Back cannot recover submitted form`() = runTest {
        val fakeRepo = FakeAccountRepository()
        val registerViewModel = RegisterViewModel(fakeRepo)

        // 1. Guest navigates to Register screen
        var guestNav = GuestNavState().enterRegister()
        registerViewModel.applyNavigationEntry(
            entryGeneration = guestNav.registerEntryGeneration,
            initialMode = guestNav.destination.initialRegisterMode!!,
        )

        // 2. Guest fills and submits registration form
        registerViewModel.onDisplayNameChanged("Test Borrower")
        registerViewModel.onEmailChanged("borrower@example.com")
        registerViewModel.onPasswordChanged("Password123!")
        registerViewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepo.registerCallCount)
        assertEquals("", registerViewModel.formState.value.password)

        // 3. SessionOwner commits Borrower session; AppNavigation observes SessionState.Authenticated
        val authenticatedSession = SessionState.Authenticated(
            key = SessionKey("uid_borrower_1", 1L),
            uid = "uid_borrower_1",
            email = "borrower@example.com",
            role = BackendRole.BORROWER,
            displayName = "Test Borrower",
        )
        val landingDestination = NavigationConfig.getDestinationForRole(authenticatedSession.role)
        assertEquals(NavDestination.AuthenticatedLanding, landingDestination)

        // AppNavigation resets guest navigation state when session becomes authenticated
        guestNav = guestNav.onSessionAuthenticated()
        assertEquals(GuestDestination.SignIn, guestNav.destination)
        assertNull(guestNav.destination.initialRegisterMode)

        // 4. Even after session exit or re-entry into registration, the submitted form cannot be recovered
        val reEnteredRegister = guestNav.enterRegister()
        registerViewModel.applyNavigationEntry(
            entryGeneration = reEnteredRegister.registerEntryGeneration,
            initialMode = reEnteredRegister.destination.initialRegisterMode!!,
        )

        val postResetState = registerViewModel.formState.value
        assertEquals("", postResetState.displayName)
        assertEquals("", postResetState.email)
        assertEquals("", postResetState.password)
        assertEquals(RegisterMode.CreateAccount, postResetState.mode)
        assertFalse(postResetState.isSubmitting)
        assertNull(postResetState.error)
    }

    @Test
    fun `navigating back from registration to sign in clears form so Back cannot recover submitted or entered inputs`() {
        val fakeRepo = FakeAccountRepository()
        val registerViewModel = RegisterViewModel(fakeRepo)

        var guestNav = GuestNavState().enterCompleteRegistration()
        registerViewModel.applyNavigationEntry(
            entryGeneration = guestNav.registerEntryGeneration,
            initialMode = guestNav.destination.initialRegisterMode!!,
        )

        registerViewModel.onDisplayNameChanged("Partial Borrower")
        registerViewModel.onEmailChanged("partial@example.com")
        registerViewModel.onPasswordChanged("Secret123!")

        // Simulate Back press / navigate back to SignIn from RegisterRoute
        registerViewModel.resetForm()
        guestNav = guestNav.returnToSignIn()

        assertEquals(GuestDestination.SignIn, guestNav.destination)
        assertEquals("", registerViewModel.formState.value.displayName)
        assertEquals("", registerViewModel.formState.value.email)
        assertEquals("", registerViewModel.formState.value.password)
    }

    @Test
    fun `SignIn error messages for MissingProfile and CompletionNeeded reference Finish registration`() {
        val missingProfileMsg = formatErrorMessage(SignInResult.MissingProfile)
        assertTrue(missingProfileMsg.contains("Finish registration"))

        val completionNeededMsg = formatErrorMessage(SignInResult.CompletionNeeded)
        assertTrue(completionNeededMsg.contains("Finish registration"))
    }

    @Test
    fun `authenticated session for all supported roles maps to shared AuthenticatedLanding`() {
        val keyBorrower = SessionKey("user123", 1L)
        val stateBorrower = SessionState.Authenticated(
            key = keyBorrower,
            uid = "user123",
            email = "borrower@example.com",
            role = BackendRole.BORROWER
        )

        assertTrue(stateBorrower.isAuthenticated)
        assertEquals(keyBorrower, stateBorrower.sessionKey)
        assertEquals("user123", stateBorrower.currentUid)

        assertEquals(
            NavDestination.AuthenticatedLanding,
            NavigationConfig.getDestinationForRole(stateBorrower.role)
        )

        val keyBroker = SessionKey("broker123", 1L)
        val stateBroker = SessionState.Authenticated(
            key = keyBroker,
            uid = "broker123",
            email = "broker@example.com",
            role = BackendRole.BROKER
        )

        assertEquals(
            NavDestination.AuthenticatedLanding,
            NavigationConfig.getDestinationForRole(stateBroker.role)
        )

        val keyUnderwriter = SessionKey("uw123", 1L)
        val stateUnderwriter = SessionState.Authenticated(
            key = keyUnderwriter,
            uid = "uw123",
            email = "underwriter@example.com",
            role = BackendRole.UNDERWRITER
        )

        assertEquals(
            NavDestination.AuthenticatedLanding,
            NavigationConfig.getDestinationForRole(stateUnderwriter.role)
        )
    }

    @Test
    fun `unknown or null role returns null private destination`() {
        assertNull(NavigationConfig.getDestinationForRole(null))
        val unknownRole = BackendRole.fromValue("unknown_role")
        assertNull(NavigationConfig.getDestinationForRole(unknownRole))
    }

    @Test
    fun `distinct session generations produce non-equal session keys`() {
        val keyGen1 = SessionKey("user123", 1L)
        val keyGen2 = SessionKey("user123", 2L)
        val keyUserB = SessionKey("user456", 1L)

        assertFalse(keyGen1 == keyGen2)
        assertFalse(keyGen1 == keyUserB)
    }

    private class FakeAccountRepository : AccountRepository {
        var registerCallCount = 0
        var completeRegistrationCallCount = 0

        override suspend fun signIn(email: String, password: String): SignInResult = SignInResult.Success
        override suspend fun restoreSession(): SignInResult = SignInResult.Success

        override suspend fun register(
            email: String,
            password: String,
            displayName: String,
        ): SignInResult {
            registerCallCount++
            return SignInResult.Success
        }

        override suspend fun completeRegistration(
            email: String,
            password: String,
            displayName: String,
        ): SignInResult {
            completeRegistrationCallCount++
            return SignInResult.Success
        }
    }
}
