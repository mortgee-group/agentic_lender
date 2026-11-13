package com.mortgee.al_mobile.di

import com.mortgee.al_mobile.data.network.ProfileApiService
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HiltModulesTest {

    @Test
    fun `NetworkModule provides Moshi and ProfileApiService`() {
        val moshi = NetworkModule.provideMoshi()
        assertNotNull(moshi)

        val okHttpClient = OkHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val profileService = NetworkModule.provideProfileApiService(retrofit)
        assertNotNull(profileService)
    }

    @Test
    fun `SessionModule provides PartitionIoInitializer`() = runTest {
        val initializer = SessionModule.providePartitionIoInitializer()
        assertNotNull(initializer)
        assertTrue(initializer.initializePartition("test_uid"))
    }
}
