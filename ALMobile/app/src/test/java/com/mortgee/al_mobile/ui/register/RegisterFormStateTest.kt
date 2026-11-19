package com.mortgee.al_mobile.ui.register

import com.mortgee.al_mobile.data.model.SignInResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterFormStateTest {

    @Test
    fun `default state has empty fields, CreateAccount mode, and no submission in progress`() {
        val state = RegisterFormState()

        assertEquals("", state.displayName)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals(RegisterMode.CreateAccount, state.mode)
        assertFalse(state.isCompletionMode)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
        assertNull(state.displayNameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.formError)
        assertFalse(state.inputIsValid)
        assertFalse(state.canSubmit)
        assertFalse(state.canOfferCompletion)
    }

    @Test
    fun `inputIsValid requires displayName, email, and password to be non-blank`() {
        assertFalse(RegisterFormState().inputIsValid)

        assertFalse(
            RegisterFormState(
                displayName = "",
                email = "borrower@example.com",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                displayName = "Test Borrower",
                email = "",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                displayName = "Test Borrower",
                email = "borrower@example.com",
                password = "",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                displayName = "   ",
                email = "   ",
                password = "   ",
            ).inputIsValid
        )

        val validState = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "Password123!",
        )
        assertTrue(validState.isDisplayNameValid)
        assertTrue(validState.isEmailValid)
        assertTrue(validState.isPasswordValid)
        assertTrue(validState.inputIsValid)
    }

    @Test
    fun `canSubmit requires valid input and not submitting`() {
        val validState = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "Password123!",
        )
        assertTrue(validState.canSubmit)

        val submittingState = validState.copy(isSubmitting = true)
        assertFalse(submittingState.canSubmit)

        val invalidState = validState.copy(displayName = "   ")
        assertFalse(invalidState.canSubmit)
    }

    @Test
    fun `validate marks blank fields and preserves submitted password exactly`() {
        val rawPassword = "  ExactP@ssw0rd!2026  "
        val invalidState = RegisterFormState(
            displayName = "   ",
            email = "",
            password = rawPassword,
        )

        val validated = invalidState.validate()

        assertEquals(SignInResult.InvalidDisplayName, validated.displayNameError)
        assertEquals(SignInResult.InvalidEmail, validated.emailError)
        assertNull(validated.passwordError)
        assertEquals(SignInResult.InvalidDisplayName, validated.error)
        assertEquals(rawPassword, validated.password)

        val missingPasswordState = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "",
        ).validate()

        assertNull(missingPasswordState.displayNameError)
        assertNull(missingPasswordState.emailError)
        assertEquals(SignInResult.WeakPassword, missingPasswordState.passwordError)
        assertEquals(SignInResult.WeakPassword, missingPasswordState.error)
    }

    @Test
    fun `field update helpers clear only the corresponding field error`() {
        val validated = RegisterFormState().validate()
        assertEquals(SignInResult.InvalidDisplayName, validated.displayNameError)
        assertEquals(SignInResult.InvalidEmail, validated.emailError)
        assertEquals(SignInResult.WeakPassword, validated.passwordError)

        val afterName = validated.withDisplayName("Test Borrower")
        assertEquals("Test Borrower", afterName.displayName)
        assertNull(afterName.displayNameError)
        assertEquals(SignInResult.InvalidEmail, afterName.emailError)
        assertEquals(SignInResult.WeakPassword, afterName.passwordError)

        val afterEmail = afterName.withEmail("borrower@example.com")
        assertEquals("borrower@example.com", afterEmail.email)
        assertNull(afterEmail.emailError)
        assertEquals(SignInResult.WeakPassword, afterEmail.passwordError)

        val afterPassword = afterEmail.withPassword("Secret123!")
        assertEquals("Secret123!", afterPassword.password)
        assertNull(afterPassword.passwordError)
        assertTrue(afterPassword.canSubmit)
    }

    @Test
    fun `toString masks password and keeps raw password out of state dumps`() {
        val rawPassword = "SuperSecretRegisterPassword123!"
        val state = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = rawPassword,
            mode = RegisterMode.CreateAccount,
        )

        val stringDump = state.toString()

        assertFalse("State dump must not contain cleartext password", stringDump.contains(rawPassword))
        assertTrue("State dump must contain masked password token", stringDump.contains("[REDACTED]"))
        assertTrue("State dump must contain displayName", stringDump.contains("Test Borrower"))
        assertTrue("State dump must contain email", stringDump.contains("borrower@example.com"))
    }

    @Test
    fun `toString indicates empty password when password is empty`() {
        val state = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "",
        )
        val stringDump = state.toString()

        assertTrue(stringDump.contains("<empty>"))
        assertTrue(stringDump.contains("Test Borrower"))
        assertTrue(stringDump.contains("borrower@example.com"))
    }

    @Test
    fun `onAttemptStarted sets isSubmitting to true and clears previous field and form errors`() {
        val state = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "Password123!",
            error = SignInResult.Connectivity,
            displayNameError = SignInResult.InvalidDisplayName,
            emailError = SignInResult.InvalidEmail,
            passwordError = SignInResult.WeakPassword,
        )

        val started = state.onAttemptStarted()

        assertTrue(started.isSubmitting)
        assertNull(started.error)
        assertNull(started.displayNameError)
        assertNull(started.emailError)
        assertNull(started.passwordError)
        assertEquals("Test Borrower", started.displayName)
        assertEquals("borrower@example.com", started.email)
        assertEquals("Password123!", started.password)
    }

    @Test
    fun `onAttemptEnded preserves approved fields displayName and email, clears password, and maps field errors`() {
        val initial = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "Password123!",
            isSubmitting = true,
        )

        val emailRejected = initial.onAttemptEnded(SignInResult.InvalidEmail)
        assertEquals("Test Borrower", emailRejected.displayName)
        assertEquals("borrower@example.com", emailRejected.email)
        assertEquals("", emailRejected.password)
        assertFalse(emailRejected.isSubmitting)
        assertEquals(SignInResult.InvalidEmail, emailRejected.emailError)
        assertNull(emailRejected.displayNameError)
        assertNull(emailRejected.passwordError)
        assertNull(emailRejected.formError)
        assertEquals(RegisterMode.CreateAccount, emailRejected.mode)

        val weakPasswordRejected = initial.onAttemptEnded(SignInResult.WeakPassword)
        assertEquals(SignInResult.WeakPassword, weakPasswordRejected.passwordError)
        assertNull(weakPasswordRejected.formError)
        assertEquals("", weakPasswordRejected.password)
    }

    @Test
    fun `onAttemptEnded with InvalidDisplayName retains field error and switches mode to CompleteExisting`() {
        val initial = RegisterFormState(
            displayName = "Bad Name",
            email = "borrower@example.com",
            password = "Password123!",
            mode = RegisterMode.CreateAccount,
            isSubmitting = true,
        )

        val ended = initial.onAttemptEnded(SignInResult.InvalidDisplayName)

        assertEquals("Bad Name", ended.displayName)
        assertEquals("borrower@example.com", ended.email)
        assertEquals("", ended.password)
        assertFalse(ended.isSubmitting)
        assertEquals(SignInResult.InvalidDisplayName, ended.displayNameError)
        assertEquals(SignInResult.InvalidDisplayName, ended.error)
        assertNull(ended.formError)
        assertEquals(RegisterMode.CompleteExisting, ended.mode)
        assertTrue(ended.isCompletionMode)
    }

    @Test
    fun `onAttemptEnded with AccountExists or CompletionNeeded exposes formError and offers completion mode`() {
        val initial = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "Password123!",
            isSubmitting = true,
        )

        val duplicateState = initial.onAttemptEnded(SignInResult.AccountExists)
        assertEquals(SignInResult.AccountExists, duplicateState.error)
        assertEquals(SignInResult.AccountExists, duplicateState.formError)
        assertTrue(duplicateState.canOfferCompletion)
        assertEquals("", duplicateState.password)

        val completionNeededState = initial.onAttemptEnded(SignInResult.CompletionNeeded)
        assertEquals(SignInResult.CompletionNeeded, completionNeededState.error)
        assertEquals(SignInResult.CompletionNeeded, completionNeededState.formError)
        assertTrue(completionNeededState.canOfferCompletion)

        val completionSelected = completionNeededState.selectCompletionMode()
        assertEquals(RegisterMode.CompleteExisting, completionSelected.mode)
        assertTrue(completionSelected.isCompletionMode)
        assertNull(completionSelected.error)
    }

    @Test
    fun `clearPassword and clearErrors preserve approved fields while clearing transient password and errors`() {
        val state = RegisterFormState(
            displayName = "Test Borrower",
            email = "borrower@example.com",
            password = "SecretPassword123!",
            mode = RegisterMode.CompleteExisting,
            error = SignInResult.InvalidDisplayName,
            displayNameError = SignInResult.InvalidDisplayName,
            emailError = SignInResult.InvalidEmail,
            passwordError = SignInResult.WeakPassword,
        )

        val passwordCleared = state.clearPassword()
        assertEquals("Test Borrower", passwordCleared.displayName)
        assertEquals("borrower@example.com", passwordCleared.email)
        assertEquals("", passwordCleared.password)
        assertEquals(RegisterMode.CompleteExisting, passwordCleared.mode)

        val errorsCleared = passwordCleared.clearErrors()
        assertNull(errorsCleared.error)
        assertNull(errorsCleared.displayNameError)
        assertNull(errorsCleared.emailError)
        assertNull(errorsCleared.passwordError)
        assertEquals(RegisterMode.CompleteExisting, errorsCleared.mode)

        val createModeSelected = errorsCleared.selectCreateAccountMode()
        assertEquals(RegisterMode.CreateAccount, createModeSelected.mode)
        assertFalse(createModeSelected.isCompletionMode)
    }

    @Test
    fun `invalid local input fails validation and makes zero repository calls`() {
        var repositoryCalls = 0
        val submitIfValid: (RegisterFormState) -> RegisterFormState = { current ->
            val validated = current.validate()
            if (validated.canSubmit) {
                repositoryCalls++
                validated.onAttemptStarted()
            } else {
                validated
            }
        }

        // Blank display name
        val blankName = submitIfValid(
            RegisterFormState(
                displayName = "   ",
                email = "borrower@example.com",
                password = "Password123!",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.InvalidDisplayName, blankName.displayNameError)
        assertEquals("Password123!", blankName.password)

        // Blank email
        val blankEmail = submitIfValid(
            RegisterFormState(
                displayName = "Test Borrower",
                email = "   ",
                password = "Password123!",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.InvalidEmail, blankEmail.emailError)
        assertEquals("Password123!", blankEmail.password)

        // Missing password
        val missingPassword = submitIfValid(
            RegisterFormState(
                displayName = "Test Borrower",
                email = "borrower@example.com",
                password = "",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.WeakPassword, missingPassword.passwordError)
    }
}
