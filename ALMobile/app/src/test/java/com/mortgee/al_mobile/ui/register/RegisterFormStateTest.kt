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

        assertEquals("", state.firstName)
        assertEquals("", state.lastName)
        assertEquals("", state.phoneNumber)
        assertNull(state.normalizedPhoneNumber)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals(RegisterMode.CreateAccount, state.mode)
        assertFalse(state.isCompletionMode)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
        assertNull(state.firstNameError)
        assertNull(state.lastNameError)
        assertNull(state.phoneNumberError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.formError)
        assertFalse(state.inputIsValid)
        assertFalse(state.canSubmit)
        assertFalse(state.canOfferCompletion)
    }

    @Test
    fun `inputIsValid requires firstName 1-30, lastName 1-50, optional phoneNumber up to 20, email, and password`() {
        assertFalse(RegisterFormState().inputIsValid)

        assertFalse(
            RegisterFormState(
                firstName = "",
                lastName = "Borrower",
                email = "borrower@example.com",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                firstName = "a".repeat(31),
                lastName = "Borrower",
                email = "borrower@example.com",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                firstName = "Test",
                lastName = "",
                email = "borrower@example.com",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                firstName = "Test",
                lastName = "b".repeat(51),
                email = "borrower@example.com",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                firstName = "Test",
                lastName = "Borrower",
                phoneNumber = "1".repeat(21),
                email = "borrower@example.com",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                firstName = "Test",
                lastName = "Borrower",
                email = "",
                password = "Password123!",
            ).inputIsValid
        )
        assertFalse(
            RegisterFormState(
                firstName = "Test",
                lastName = "Borrower",
                email = "borrower@example.com",
                password = "",
            ).inputIsValid
        )

        val validWithoutPhone = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
            email = "borrower@example.com",
            password = "Password123!",
        )
        assertTrue(validWithoutPhone.isFirstNameValid)
        assertTrue(validWithoutPhone.isLastNameValid)
        assertTrue(validWithoutPhone.isPhoneNumberValid)
        assertNull(validWithoutPhone.normalizedPhoneNumber)
        assertTrue(validWithoutPhone.isEmailValid)
        assertTrue(validWithoutPhone.isPasswordValid)
        assertTrue(validWithoutPhone.inputIsValid)

        val validWithPhone = validWithoutPhone.copy(phoneNumber = "+353871234567")
        assertTrue(validWithPhone.isPhoneNumberValid)
        assertEquals("+353871234567", validWithPhone.normalizedPhoneNumber)
        assertTrue(validWithPhone.inputIsValid)
    }

    @Test
    fun `canSubmit requires valid input and not submitting`() {
        val validState = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
            email = "borrower@example.com",
            password = "Password123!",
        )
        assertTrue(validState.canSubmit)

        val submittingState = validState.copy(isSubmitting = true)
        assertFalse(submittingState.canSubmit)

        val invalidState = validState.copy(firstName = "   ")
        assertFalse(invalidState.canSubmit)
    }

    @Test
    fun `validate marks invalid fields and preserves submitted password exactly`() {
        val rawPassword = "  ExactP@ssw0rd!2026  "
        val invalidState = RegisterFormState(
            firstName = "   ",
            lastName = "",
            phoneNumber = "1".repeat(25),
            email = "",
            password = rawPassword,
        )

        val validated = invalidState.validate()

        assertEquals(SignInResult.InvalidFirstName, validated.firstNameError)
        assertEquals(SignInResult.InvalidLastName, validated.lastNameError)
        assertEquals(SignInResult.InvalidPhoneNumber, validated.phoneNumberError)
        assertEquals(SignInResult.InvalidEmail, validated.emailError)
        assertNull(validated.passwordError)
        assertEquals(SignInResult.InvalidFirstName, validated.error)
        assertEquals(rawPassword, validated.password)

        val missingPasswordState = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
            email = "borrower@example.com",
            password = "",
        ).validate()

        assertNull(missingPasswordState.firstNameError)
        assertNull(missingPasswordState.lastNameError)
        assertNull(missingPasswordState.phoneNumberError)
        assertNull(missingPasswordState.emailError)
        assertEquals(SignInResult.WeakPassword, missingPasswordState.passwordError)
        assertEquals(SignInResult.WeakPassword, missingPasswordState.error)
    }

    @Test
    fun `field update helpers clear only the corresponding field error`() {
        val validated = RegisterFormState(
            phoneNumber = "1".repeat(25),
        ).validate()
        assertEquals(SignInResult.InvalidFirstName, validated.firstNameError)
        assertEquals(SignInResult.InvalidLastName, validated.lastNameError)
        assertEquals(SignInResult.InvalidPhoneNumber, validated.phoneNumberError)
        assertEquals(SignInResult.InvalidEmail, validated.emailError)
        assertEquals(SignInResult.WeakPassword, validated.passwordError)

        val afterFirstName = validated.withFirstName("Test")
        assertEquals("Test", afterFirstName.firstName)
        assertNull(afterFirstName.firstNameError)
        assertEquals(SignInResult.InvalidLastName, afterFirstName.lastNameError)

        val afterLastName = afterFirstName.withLastName("Borrower")
        assertEquals("Borrower", afterLastName.lastName)
        assertNull(afterLastName.lastNameError)
        assertEquals(SignInResult.InvalidPhoneNumber, afterLastName.phoneNumberError)

        val afterPhone = afterLastName.withPhoneNumber("+353871234567")
        assertEquals("+353871234567", afterPhone.phoneNumber)
        assertNull(afterPhone.phoneNumberError)
        assertEquals(SignInResult.InvalidEmail, afterPhone.emailError)

        val afterEmail = afterPhone.withEmail("borrower@example.com")
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
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "+353871234567",
            email = "borrower@example.com",
            password = rawPassword,
            mode = RegisterMode.CreateAccount,
        )

        val stringDump = state.toString()

        assertFalse("State dump must not contain cleartext password", stringDump.contains(rawPassword))
        assertTrue("State dump must contain masked password token", stringDump.contains("[REDACTED]"))
        assertTrue("State dump must contain firstName", stringDump.contains("Test"))
        assertTrue("State dump must contain lastName", stringDump.contains("Borrower"))
        assertTrue("State dump must contain email", stringDump.contains("borrower@example.com"))
    }

    @Test
    fun `toString indicates empty password when password is empty`() {
        val state = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
            email = "borrower@example.com",
            password = "",
        )
        val stringDump = state.toString()

        assertTrue(stringDump.contains("<empty>"))
        assertTrue(stringDump.contains("Test"))
        assertTrue(stringDump.contains("Borrower"))
        assertTrue(stringDump.contains("borrower@example.com"))
    }

    @Test
    fun `onAttemptStarted sets isSubmitting to true and clears previous field and form errors`() {
        val state = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "+353871234567",
            email = "borrower@example.com",
            password = "Password123!",
            error = SignInResult.Connectivity,
            firstNameError = SignInResult.InvalidFirstName,
            lastNameError = SignInResult.InvalidLastName,
            phoneNumberError = SignInResult.InvalidPhoneNumber,
            emailError = SignInResult.InvalidEmail,
            passwordError = SignInResult.WeakPassword,
        )

        val started = state.onAttemptStarted()

        assertTrue(started.isSubmitting)
        assertNull(started.error)
        assertNull(started.firstNameError)
        assertNull(started.lastNameError)
        assertNull(started.phoneNumberError)
        assertNull(started.emailError)
        assertNull(started.passwordError)
        assertEquals("Test", started.firstName)
        assertEquals("Borrower", started.lastName)
        assertEquals("+353871234567", started.phoneNumber)
        assertEquals("borrower@example.com", started.email)
        assertEquals("Password123!", started.password)
    }

    @Test
    fun `onAttemptEnded preserves approved fields, clears password, and maps field errors`() {
        val initial = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "+353871234567",
            email = "borrower@example.com",
            password = "Password123!",
            isSubmitting = true,
        )

        val emailRejected = initial.onAttemptEnded(SignInResult.InvalidEmail)
        assertEquals("Test", emailRejected.firstName)
        assertEquals("Borrower", emailRejected.lastName)
        assertEquals("+353871234567", emailRejected.phoneNumber)
        assertEquals("borrower@example.com", emailRejected.email)
        assertEquals("", emailRejected.password)
        assertFalse(emailRejected.isSubmitting)
        assertEquals(SignInResult.InvalidEmail, emailRejected.emailError)
        assertNull(emailRejected.firstNameError)
        assertNull(emailRejected.lastNameError)
        assertNull(emailRejected.phoneNumberError)
        assertNull(emailRejected.passwordError)
        assertNull(emailRejected.formError)
        assertEquals(RegisterMode.CreateAccount, emailRejected.mode)

        val weakPasswordRejected = initial.onAttemptEnded(SignInResult.WeakPassword)
        assertEquals(SignInResult.WeakPassword, weakPasswordRejected.passwordError)
        assertNull(weakPasswordRejected.formError)
        assertEquals("", weakPasswordRejected.password)
    }

    @Test
    fun `onAttemptEnded with correctable profile field error retains field error and switches mode to CompleteExisting`() {
        val initial = RegisterFormState(
            firstName = "BadName",
            lastName = "Borrower",
            phoneNumber = "+353871234567",
            email = "borrower@example.com",
            password = "Password123!",
            mode = RegisterMode.CreateAccount,
            isSubmitting = true,
        )

        val endedFirstName = initial.onAttemptEnded(SignInResult.InvalidFirstName)
        assertEquals("BadName", endedFirstName.firstName)
        assertEquals("Borrower", endedFirstName.lastName)
        assertEquals("borrower@example.com", endedFirstName.email)
        assertEquals("", endedFirstName.password)
        assertFalse(endedFirstName.isSubmitting)
        assertEquals(SignInResult.InvalidFirstName, endedFirstName.firstNameError)
        assertEquals(SignInResult.InvalidFirstName, endedFirstName.error)
        assertNull(endedFirstName.formError)
        assertEquals(RegisterMode.CompleteExisting, endedFirstName.mode)
        assertTrue(endedFirstName.isCompletionMode)

        val endedLastName = initial.onAttemptEnded(SignInResult.InvalidLastName)
        assertEquals(SignInResult.InvalidLastName, endedLastName.lastNameError)
        assertEquals(RegisterMode.CompleteExisting, endedLastName.mode)

        val endedPhone = initial.onAttemptEnded(SignInResult.InvalidPhoneNumber)
        assertEquals(SignInResult.InvalidPhoneNumber, endedPhone.phoneNumberError)
        assertEquals(RegisterMode.CompleteExisting, endedPhone.mode)
    }

    @Test
    fun `onAttemptEnded with AccountExists or CompletionNeeded exposes formError and offers completion mode`() {
        val initial = RegisterFormState(
            firstName = "Test",
            lastName = "Borrower",
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
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "+353871234567",
            email = "borrower@example.com",
            password = "SecretPassword123!",
            mode = RegisterMode.CompleteExisting,
            error = SignInResult.InvalidFirstName,
            firstNameError = SignInResult.InvalidFirstName,
            lastNameError = SignInResult.InvalidLastName,
            phoneNumberError = SignInResult.InvalidPhoneNumber,
            emailError = SignInResult.InvalidEmail,
            passwordError = SignInResult.WeakPassword,
        )

        val passwordCleared = state.clearPassword()
        assertEquals("Test", passwordCleared.firstName)
        assertEquals("Borrower", passwordCleared.lastName)
        assertEquals("+353871234567", passwordCleared.phoneNumber)
        assertEquals("borrower@example.com", passwordCleared.email)
        assertEquals("", passwordCleared.password)
        assertEquals(RegisterMode.CompleteExisting, passwordCleared.mode)

        val errorsCleared = passwordCleared.clearErrors()
        assertNull(errorsCleared.error)
        assertNull(errorsCleared.firstNameError)
        assertNull(errorsCleared.lastNameError)
        assertNull(errorsCleared.phoneNumberError)
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

        // Blank first name
        val blankFirstName = submitIfValid(
            RegisterFormState(
                firstName = "   ",
                lastName = "Borrower",
                email = "borrower@example.com",
                password = "Password123!",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.InvalidFirstName, blankFirstName.firstNameError)
        assertEquals("Password123!", blankFirstName.password)

        // Blank last name
        val blankLastName = submitIfValid(
            RegisterFormState(
                firstName = "Test",
                lastName = "   ",
                email = "borrower@example.com",
                password = "Password123!",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.InvalidLastName, blankLastName.lastNameError)
        assertEquals("Password123!", blankLastName.password)

        // Overlength phone number
        val overlengthPhone = submitIfValid(
            RegisterFormState(
                firstName = "Test",
                lastName = "Borrower",
                phoneNumber = "123456789012345678901",
                email = "borrower@example.com",
                password = "Password123!",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.InvalidPhoneNumber, overlengthPhone.phoneNumberError)

        // Blank email
        val blankEmail = submitIfValid(
            RegisterFormState(
                firstName = "Test",
                lastName = "Borrower",
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
                firstName = "Test",
                lastName = "Borrower",
                email = "borrower@example.com",
                password = "",
            )
        )
        assertEquals(0, repositoryCalls)
        assertEquals(SignInResult.WeakPassword, missingPassword.passwordError)
    }
}
