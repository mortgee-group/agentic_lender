package com.mortgee.al_mobile.di

import com.google.firebase.auth.FirebaseAuth
import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.auth.FirebaseAuthAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideAuthAdapter(
        firebaseAuth: FirebaseAuth,
    ): AuthAdapter {
        return FirebaseAuthAdapter(firebaseAuth)
    }
}
