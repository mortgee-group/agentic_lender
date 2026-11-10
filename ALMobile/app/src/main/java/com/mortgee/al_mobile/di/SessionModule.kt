package com.mortgee.al_mobile.di

import com.mortgee.al_mobile.data.session.DefaultSessionOwner
import com.mortgee.al_mobile.data.session.SessionOwner
import dagger.Binds
import dagger.Module
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
}
