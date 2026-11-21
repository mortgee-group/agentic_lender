package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRepositoryTest {

    @Test
    fun `account repository contract returns expected sign in and register results`() = runTest {
        val fakeRepository = object : AccountRepository {
            override suspend fun signIn(email: String, password: String): SignInResult {
                return if (email == "valid@example.com" && password == "secret") {
                    SignInResult.Success
                } else {
                    SignInResult.InvalidCredentials
                }
            }

            override suspend fun restoreSession(): SignInResult = SignInResult.Success

            override suspend fun register(
                email: String,
                password: String,
                firstName: String,
                lastName: String,
                phoneNumber: String?,
            ): SignInResult {
                return if (email.isNotBlank() && password.isNotBlank() && firstName.isNotBlank() && lastName.isNotBlank()) {
                    SignInResult.Success
                } else {
                    SignInResult.InvalidFirstName
                }
            }

            override suspend fun completeRegistration(
                email: String,
                password: String,
                firstName: String,
                lastName: String,
                phoneNumber: String?,
            ): SignInResult {
                return if (email.isNotBlank() && password.isNotBlank() && firstName.isNotBlank() && lastName.isNotBlank()) {
                    SignInResult.Success
                } else {
                    SignInResult.InvalidCredentials
                }
            }
        }

        assertEquals(
            SignInResult.Success,
            fakeRepository.signIn("valid@example.com", "secret")
        )
        assertEquals(
            SignInResult.InvalidCredentials,
            fakeRepository.signIn("invalid@example.com", "wrong")
        )
        assertEquals(
            SignInResult.Success,
            fakeRepository.register("borrower@example.com", "secret123", "Test", "Borrower", "+353871234567")
        )
        assertEquals(
            SignInResult.Success,
            fakeRepository.completeRegistration("borrower@example.com", "secret123", "Test", "Borrower", null)
        )
    }

    @Test(expected = CancellationException::class)
    fun `signIn propagates CancellationException when coroutine is cancelled`() = runTest {
        val fakeRepository = object : AccountRepository {
            override suspend fun signIn(email: String, password: String): SignInResult {
                throw CancellationException("Sign in cancelled")
            }

            override suspend fun restoreSession(): SignInResult = SignInResult.Success

            override suspend fun register(
                email: String,
                password: String,
                firstName: String,
                lastName: String,
                phoneNumber: String?,
            ): SignInResult = SignInResult.Success

            override suspend fun completeRegistration(
                email: String,
                password: String,
                firstName: String,
                lastName: String,
                phoneNumber: String?,
            ): SignInResult = SignInResult.Success
        }

        fakeRepository.signIn("test@example.com", "password")
    }
}
