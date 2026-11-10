package com.mortgee.al_mobile.di

import com.mortgee.al_mobile.data.session.DefaultSessionOwner
import com.mortgee.al_mobile.data.session.DefaultUserPartitionManager
import com.mortgee.al_mobile.data.session.PartitionIoInitializer
import com.mortgee.al_mobile.data.session.SessionOwner
import com.mortgee.al_mobile.data.session.UserPartitionManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    @Singleton
    abstract fun bindSessionOwner(
        impl: DefaultSessionOwner
    ): SessionOwner

    @Binds
    @Singleton
    abstract fun bindUserPartitionManager(
        impl: DefaultUserPartitionManager
    ): UserPartitionManager

    companion object {
        @Provides
        @Singleton
        fun providePartitionIoInitializer(): PartitionIoInitializer {
            return PartitionIoInitializer { true }
        }
    }
}
