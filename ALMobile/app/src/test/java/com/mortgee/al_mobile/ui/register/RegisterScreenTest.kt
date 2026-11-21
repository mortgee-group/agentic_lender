package com.mortgee.al_mobile.ui.register

import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mortgee.al_mobile.data.model.SignInResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterScreenTest {

    @Test
    fun `password visual transformation masks credentials by default and unmasks only when visible`() {
        val defaultTransformation = resolvePasswordVisualTransformation(isPasswordVisible = false)
        assertTrue(
            "Default password field must use PasswordVisualTransformation",
            defaultTransformation is PasswordVisualTransformation,
        )

        val visibleTransformation = resolvePasswordVisualTransformation(isPasswordVisible = true)
        assertEquals(VisualTransformation.None, visibleTransformation)
    }

    @Test
    fun `formatRegisterErrorMessage returns safe non-empty messages for all failure outcomes and empty for Success`() {
        val allFailureOutcomes = listOf(
            SignInResult.InvalidFirstName,
            SignInResult.InvalidLastName,
            SignInResult.InvalidPhoneNumber,
            SignInResult.InvalidDisplayName,
            SignInResult.InvalidEmail,
            SignInResult.WeakPassword,
            SignInResult.AccountExists,
            SignInResult.CompletionNeeded,
            SignInResult.InvalidCredentials,
            SignInResult.DisabledAccount,
            SignInResult.Connectivity,
            SignInResult.RejectedAuthentication,
            SignInResult.MissingProfile,
            SignInResult.UnsupportedRole,
            SignInResult.Busy,
            SignInResult.GenericFailure,
        )

        for (outcome in allFailureOutcomes) {
            val message = formatRegisterErrorMessage(outcome)
            assertTrue("Expected non-blank safe message for $outcome", message.isNotBlank())
            assertFalse("Message must not leak Exception class names", message.contains("Exception"))
            assertFalse("Message must not leak Bearer token prefix", message.contains("Bearer", ignoreCase = true))
        }

        assertEquals("", formatRegisterErrorMessage(SignInResult.Success))
    }

    @Test
    fun `formatRegisterErrorMessage mentions explicit Finish registration action for AccountExists and CompletionNeeded`() {
        val accountExistsMessage = formatRegisterErrorMessage(SignInResult.AccountExists)
        assertTrue(accountExistsMessage.contains("finish registration", ignoreCase = true))

        val completionNeededMessage = formatRegisterErrorMessage(SignInResult.CompletionNeeded)
        assertTrue(completionNeededMessage.contains("Finish registration"))
    }

    @Test
    fun `Already have an account Sign in label matches 04_15B specification`() {
        assertEquals(
            "Already have an account? Sign in",
            ALREADY_HAVE_ACCOUNT_SIGN_IN_LABEL,
        )
    }
}
