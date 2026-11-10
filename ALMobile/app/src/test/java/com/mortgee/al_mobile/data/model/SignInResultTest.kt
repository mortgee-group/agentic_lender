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
            }
        }

        assertEquals(9, mappedNames.size)
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
            ),
            mappedNames
        )
    }
}
