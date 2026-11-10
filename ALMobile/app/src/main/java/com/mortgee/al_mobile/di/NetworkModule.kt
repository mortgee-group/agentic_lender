package com.mortgee.al_mobile.di

import com.mortgee.al_mobile.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton
import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.network.CatalogueApiService
import com.mortgee.al_mobile.data.network.DefaultPrivateTokenSeam
import com.mortgee.al_mobile.data.network.PrivateRequestInterceptor
import com.mortgee.al_mobile.data.network.PrivateTokenSeam
import com.mortgee.al_mobile.data.network.ProfileApiService
import com.mortgee.al_mobile.data.session.SessionOwner

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun providePrivateTokenSeam(
        sessionOwner: SessionOwner,
        authAdapter: AuthAdapter,
    ): PrivateTokenSeam {
        return DefaultPrivateTokenSeam(
            sessionOwner = sessionOwner,
            authAdapter = authAdapter,
            baseUrlString = BuildConfig.API_BASE_URL
        )
    }

    @Provides
    @Singleton
    fun providePrivateRequestInterceptor(
        privateTokenSeam: PrivateTokenSeam
    ): PrivateRequestInterceptor {
        return PrivateRequestInterceptor(privateTokenSeam)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        privateRequestInterceptor: PrivateRequestInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(privateRequestInterceptor)
            .addNetworkInterceptor(privateRequestInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideCatalogueApiService(
        retrofit: Retrofit
    ): CatalogueApiService {
        return retrofit.create(CatalogueApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideProfileApiService(
        retrofit: Retrofit
    ): ProfileApiService {
        return retrofit.create(ProfileApiService::class.java)
    }
}
