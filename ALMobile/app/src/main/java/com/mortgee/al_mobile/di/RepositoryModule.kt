package com.mortgee.al_mobile.di

import com.mortgee.al_mobile.data.repository.AccountRepository
import com.mortgee.al_mobile.data.repository.ApiAccountRepository
import com.mortgee.al_mobile.data.repository.ApiCatalogueRepository
import com.mortgee.al_mobile.data.repository.CatalogueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCatalogueRepository(
        impl: ApiCatalogueRepository
    ): CatalogueRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: ApiAccountRepository
    ): AccountRepository
}
