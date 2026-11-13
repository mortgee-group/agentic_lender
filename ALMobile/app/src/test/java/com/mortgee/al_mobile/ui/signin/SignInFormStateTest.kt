package com.mortgee.al_mobile.ui.signin

import com.mortgee.al_mobile.data.model.SignInResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInFormStateTest {

    @Test
    fun `default state has empty credentials and no submission in progress`() {
        val state = SignInFormState()

        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
        assertFalse(state.inputIsValid)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `inputIsValid requires both email and password to be non-blank`() {
        val emptyState = SignInFormState()
        assertFalse(emptyState.inputIsValid)

        val emailOnlyState = SignInFormState(email = "borrower@example.com", password = "")
        assertFalse(emailOnlyState.inputIsValid)

        val passwordOnlyState = SignInFormState(email = "", password = "password123")
        assertFalse(passwordOnlyState.inputIsValid)

        val whitespaceState = SignInFormState(email = "   ", password = "   ")
        assertFalse(whitespaceState.inputIsValid)

        val validState = SignInFormState(email = "borrower@example.com", password = "password123")
        assertTrue(validState.inputIsValid)
    }

    @Test
    fun `canSubmit requires valid input and not submitting`() {
        val validState = SignInFormState(email = "borrower@example.com", password = "password123")
        assertTrue(validState.canSubmit)

        val submittingState = validState.copy(isSubmitting = true)
        assertFalse(submittingState.canSubmit)

        val invalidState = SignInFormState(email = "", password = "password123")
        assertFalse(invalidState.canSubmit)
    }

    @Test
    fun `toString masks password and keeps raw password out of state dumps`() {
        val rawPassword = "SuperSecretPassword123!"
        val state = SignInFormState(
            email = "user@example.com",
            password = rawPassword,
            isSubmitting = false,
            error = null
        )

        val stringDump = state.toString()

        assertFalse("State dump must not contain cleartext password", stringDump.contains(rawPassword))
        assertTrue("State dump must contain masked password token", stringDump.contains("[REDACTED]"))
        assertTrue("State dump must contain email", stringDump.contains("user@example.com"))
    }

    @Test
    fun `toString indicates empty password when password is empty`() {
        val state = SignInFormState(email = "user@example.com", password = "")
        val stringDump = state.toString()

        assertTrue(stringDump.contains("<empty>"))
        assertTrue(stringDump.contains("user@example.com"))
    }

    @Test
    fun `clearPassword resets password to empty string while preserving email and status`() {
        val state = SignInFormState(
            email = "user@example.com",
            password = "secretPassword",
            isSubmitting = true,
            error = SignInResult.GenericFailure
        )

        val clearedState = state.clearPassword()

        assertEquals("user@example.com", clearedState.email)
        assertEquals("", clearedState.password)
        assertTrue(clearedState.isSubmitting)
        assertEquals(SignInResult.GenericFailure, clearedState.error)
    }

    @Test
    fun `onAttemptStarted sets isSubmitting to true and clears error`() {
        val state = SignInFormState(
            email = "user@example.com",
            password = "password123",
            isSubmitting = false,
            error = SignInResult.InvalidCredentials
        )

        val startedState = state.onAttemptStarted()

        assertEquals("user@example.com", startedState.email)
        assertEquals("password123", startedState.password)
        assertTrue(startedState.isSubmitting)
        assertNull(startedState.error)
    }

    @Test
    fun `onAttemptEnded retains email, clears password, sets isSubmitting to false, and updates error`() {
        val initial = SignInFormState(
            email = "borrower@example.com",
            password = "MyPassword123",
            isSubmitting = true,
            error = null
        )

        val endedState = initial.onAttemptEnded(SignInResult.InvalidCredentials)

        assertEquals("borrower@example.com", endedState.email)
        assertEquals("", endedState.password)
        assertFalse(endedState.isSubmitting)
        assertEquals(SignInResult.InvalidCredentials, endedState.error)
    }

    @Test
    fun `retains email in memory after failure and clears password when attempt ends`() {
        var state = SignInFormState(email = "broker@example.com", password = "secretPassword")
        state = state.onAttemptStarted()

        // Simulate failed attempt
        state = state.onAttemptEnded(SignInResult.InvalidCredentials)

        // Email retained, password cleared
        assertEquals("broker@example.com", state.email)
        assertEquals("", state.password)
        assertFalse(state.isSubmitting)
        assertEquals(SignInResult.InvalidCredentials, state.error)
    }
}
