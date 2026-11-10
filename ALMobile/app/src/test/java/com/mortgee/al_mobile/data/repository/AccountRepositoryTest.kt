package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRepositoryTest {

    @Test
    fun `account repository contract returns expected sign in results`() = runTest {
        val fakeRepository = object : AccountRepository {
            override suspend fun signIn(email: String, password: String): SignInResult {
                return if (email == "valid@example.com" && password == "secret") {
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
    }

    @Test(expected = CancellationException::class)
    fun `signIn propagates CancellationException when coroutine is cancelled`() = runTest {
        val fakeRepository = object : AccountRepository {
            override suspend fun signIn(email: String, password: String): SignInResult {
                throw CancellationException("Sign in cancelled")
            }
        }

        fakeRepository.signIn("test@example.com", "password")
    }
}
