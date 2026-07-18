package com.efreedom.mutter.data.di

import com.efreedom.mutter.data.repository.AuthRepositoryImpl
import com.efreedom.mutter.data.repository.ProfileRepositoryImpl
import com.efreedom.mutter.data.service.SessionProviderImpl
import com.efreedom.mutter.domain.repository.AuthRepository
import com.efreedom.mutter.domain.repository.ProfileRepository
import com.efreedom.mutter.domain.repository.SessionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Repository 인터페이스 → 구현 바인딩(추가 구현은 여기에 이어 붙인다). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindSessionProvider(impl: SessionProviderImpl): SessionProvider
}
