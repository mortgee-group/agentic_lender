package com.mortgee.al_mobile.di

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.auth.AuthResult
import com.mortgee.al_mobile.data.auth.AuthUser
import com.mortgee.al_mobile.data.model.SignInResult
import com.mortgee.al_mobile.data.network.ProfileApiService
import com.mortgee.al_mobile.data.network.model.CreateProfileRequest
import com.mortgee.al_mobile.data.network.model.UserProfileDto
import com.mortgee.al_mobile.data.repository.AccountRepository
import com.mortgee.al_mobile.data.repository.ApiAccountRepository
import com.mortgee.al_mobile.data.session.CompletedSessionStorage
import com.mortgee.al_mobile.data.session.DefaultSessionOwner
import com.mortgee.al_mobile.data.session.DefaultUserPartitionManager
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.UserPartitionManager
import com.mortgee.al_mobile.ui.register.RegisterViewModel
import com.mortgee.al_mobile.ui.signin.SignInViewModel
import dagger.Binds
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HiltModulesTest {

    @Test
    fun `NetworkModule provides Moshi, OkHttpClient, Retrofit, PrivateTokenSeam, and ProfileApiService`() {
        val moshi = NetworkModule.provideMoshi()
        assertNotNull(moshi)

        val stubAuthAdapter = StubAuthAdapter()
        val stubStorage = StubCompletedSessionStorage()
        val sessionOwner: SessionOwner = DefaultSessionOwner(stubStorage, stubAuthAdapter)

        val privateTokenSeam = NetworkModule.providePrivateTokenSeam(sessionOwner, stubAuthAdapter)
        assertNotNull(privateTokenSeam)

        val interceptor = NetworkModule.providePrivateRequestInterceptor(privateTokenSeam)
        assertNotNull(interceptor)

        val okHttpClient = NetworkModule.provideOkHttpClient(interceptor)
        assertNotNull(okHttpClient)

        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        assertNotNull(retrofit)

        val profileService = NetworkModule.provideProfileApiService(retrofit)
        assertNotNull(profileService)
    }

    @Test
    fun `SessionModule provides PartitionIoInitializer and binds SessionOwner and UserPartitionManager`() = runTest {
        val initializer = SessionModule.providePartitionIoInitializer()
        assertNotNull(initializer)
        assertTrue(initializer.initializePartition("test_uid"))

        val bindSessionOwnerMethod = SessionModule::class.java.getDeclaredMethod(
            "bindSessionOwner",
            DefaultSessionOwner::class.java,
        )
        assertTrue(bindSessionOwnerMethod.isAnnotationPresent(Binds::class.java))
        assertEquals(SessionOwner::class.java, bindSessionOwnerMethod.returnType)

        val bindPartitionManagerMethod = SessionModule::class.java.getDeclaredMethod(
            "bindUserPartitionManager",
            DefaultUserPartitionManager::class.java,
        )
        assertTrue(bindPartitionManagerMethod.isAnnotationPresent(Binds::class.java))
        assertEquals(UserPartitionManager::class.java, bindPartitionManagerMethod.returnType)
    }

    @Test
    fun `RepositoryModule binds AccountRepository to ApiAccountRepository and resolves ViewModels`() = runTest {
        val bindAccountRepositoryMethod = RepositoryModule::class.java.getDeclaredMethod(
            "bindAccountRepository",
            ApiAccountRepository::class.java,
        )
        assertTrue(bindAccountRepositoryMethod.isAnnotationPresent(Binds::class.java))
        assertEquals(AccountRepository::class.java, bindAccountRepositoryMethod.returnType)

        val authAdapter: AuthAdapter = StubAuthAdapter()
        val storage: CompletedSessionStorage = StubCompletedSessionStorage()
        val sessionOwner: SessionOwner = DefaultSessionOwner(storage, authAdapter)
        val partitionManager: UserPartitionManager =
            DefaultUserPartitionManager(SessionModule.providePartitionIoInitializer())
        val profileApiService: ProfileApiService = object : ProfileApiService {
            override suspend fun getProfile(bearerToken: String): UserProfileDto {
                return UserProfileDto(
                    uid = "uid-1",
                    email = "borrower@example.com",
                    role = "borrower",
                    displayName = "Test Borrower",
                )
            }

            override suspend fun createProfile(
                bearerToken: String,
                request: CreateProfileRequest,
            ): UserProfileDto {
                return UserProfileDto(
                    uid = "uid-1",
                    email = "borrower@example.com",
                    role = request.role,
                    displayName = "${request.firstName} ${request.lastName}",
                    firstName = request.firstName,
                    lastName = request.lastName,
                    phoneNumber = request.phoneNumber,
                )
            }
        }

        val repository: AccountRepository = ApiAccountRepository(
            authAdapter = authAdapter,
            profileApiService = profileApiService,
            sessionOwner = sessionOwner,
            userPartitionManager = partitionManager,
            completedSessionStorage = storage,
        )

        val registerViewModel = RegisterViewModel(repository)
        val signInViewModel = SignInViewModel(repository)
        assertNotNull(registerViewModel.formState.value)
        assertNotNull(signInViewModel.formState.value)
    }

    private class StubAuthAdapter : AuthAdapter {
        override val currentUid: String = "uid-1"

        override suspend fun signIn(email: String, password: String): AuthResult =
            AuthResult.Success(StubAuthUser())

        override suspend fun createAccount(email: String, password: String): AuthResult =
            AuthResult.Success(StubAuthUser())

        override suspend fun getToken(uid: String, forceRefresh: Boolean): Result<String> =
            Result.success("token-1")

        override suspend fun signOut(): Boolean = true
    }

    private class StubAuthUser : AuthUser {
        override val uid: String = "uid-1"
        override val email: String = "borrower@example.com"
        override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
            Result.success("token-1")
    }

    private class StubCompletedSessionStorage : CompletedSessionStorage {
        private var savedUid: String? = null
        override suspend fun getCompletedUid(): String? = savedUid
        override suspend fun saveCompletedUid(uid: String): Boolean {
            savedUid = uid
            return true
        }
        override suspend fun clearCompletedUid(): Boolean {
            savedUid = null
            return true
        }
    }
}

