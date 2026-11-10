package com.mortgee.al_mobile.di

import android.content.Context
import android.content.SharedPreferences
import com.mortgee.al_mobile.BuildConfig
import com.mortgee.al_mobile.data.session.CompletedSessionStorage
import com.mortgee.al_mobile.data.session.DefaultUserPrivateStore
import com.mortgee.al_mobile.data.session.SharedPreferencesCompletedSessionStorage
import com.mortgee.al_mobile.data.session.UserPrivateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val storageName = SharedPreferencesCompletedSessionStorage.getStorageName(BuildConfig.API_BASE_URL)
        return context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideCompletedSessionStorage(
        impl: SharedPreferencesCompletedSessionStorage
    ): CompletedSessionStorage {
        return impl
    }

    @Provides
    @Singleton
    fun provideUserPrivateStore(
        impl: DefaultUserPrivateStore
    ): UserPrivateStore {
        return impl
    }
}
