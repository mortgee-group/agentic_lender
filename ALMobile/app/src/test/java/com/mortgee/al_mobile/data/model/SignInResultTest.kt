package com.mortgee.al_mobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInResultTest {

    @Test
    fun `isSuccess returns true only for Success outcome`() {
        assertTrue(SignInResult.Success.isSuccess)
        assertFalse(SignInResult.Busy.isSuccess)
        assertFalse(SignInResult.InvalidCredentials.isSuccess)
        assertFalse(SignInResult.DisabledAccount.isSuccess)
        assertFalse(SignInResult.Connectivity.isSuccess)
        assertFalse(SignInResult.RejectedAuthentication.isSuccess)
        assertFalse(SignInResult.MissingProfile.isSuccess)
        assertFalse(SignInResult.UnsupportedRole.isSuccess)
        assertFalse(SignInResult.GenericFailure.isSuccess)
        assertFalse(SignInResult.InvalidEmail.isSuccess)
        assertFalse(SignInResult.WeakPassword.isSuccess)
        assertFalse(SignInResult.InvalidFirstName.isSuccess)
        assertFalse(SignInResult.InvalidLastName.isSuccess)
        assertFalse(SignInResult.InvalidPhoneNumber.isSuccess)
        assertFalse(SignInResult.InvalidDisplayName.isSuccess)
        assertFalse(SignInResult.AccountExists.isSuccess)
        assertFalse(SignInResult.CompletionNeeded.isSuccess)
    }

    @Test
    fun `exhaustive pattern matching covers all safe outcomes`() {
        val outcomes: List<SignInResult> = listOf(
            SignInResult.Success,
            SignInResult.Busy,
            SignInResult.InvalidCredentials,
            SignInResult.DisabledAccount,
            SignInResult.Connectivity,
            SignInResult.RejectedAuthentication,
            SignInResult.MissingProfile,
            SignInResult.UnsupportedRole,
            SignInResult.GenericFailure,
            SignInResult.InvalidEmail,
            SignInResult.WeakPassword,
            SignInResult.InvalidFirstName,
            SignInResult.InvalidLastName,
            SignInResult.InvalidPhoneNumber,
            SignInResult.InvalidDisplayName,
            SignInResult.AccountExists,
            SignInResult.CompletionNeeded,
        )

        val mappedNames = outcomes.map { result ->
            when (result) {
                SignInResult.Success -> "Success"
                SignInResult.Busy -> "Busy"
                SignInResult.InvalidCredentials -> "InvalidCredentials"
                SignInResult.DisabledAccount -> "DisabledAccount"
                SignInResult.Connectivity -> "Connectivity"
                SignInResult.RejectedAuthentication -> "RejectedAuthentication"
                SignInResult.MissingProfile -> "MissingProfile"
                SignInResult.UnsupportedRole -> "UnsupportedRole"
                SignInResult.GenericFailure -> "GenericFailure"
                SignInResult.InvalidEmail -> "InvalidEmail"
                SignInResult.WeakPassword -> "WeakPassword"
                SignInResult.InvalidFirstName -> "InvalidFirstName"
                SignInResult.InvalidLastName -> "InvalidLastName"
                SignInResult.InvalidPhoneNumber -> "InvalidPhoneNumber"
                SignInResult.InvalidDisplayName -> "InvalidDisplayName"
                SignInResult.AccountExists -> "AccountExists"
                SignInResult.CompletionNeeded -> "CompletionNeeded"
            }
        }

        assertEquals(17, mappedNames.size)
        assertEquals(
            listOf(
                "Success",
                "Busy",
                "InvalidCredentials",
                "DisabledAccount",
                "Connectivity",
                "RejectedAuthentication",
                "MissingProfile",
                "UnsupportedRole",
                "GenericFailure",
                "InvalidEmail",
                "WeakPassword",
                "InvalidFirstName",
                "InvalidLastName",
                "InvalidPhoneNumber",
                "InvalidDisplayName",
                "AccountExists",
                "CompletionNeeded",
            ),
            mappedNames
        )
    }
}
