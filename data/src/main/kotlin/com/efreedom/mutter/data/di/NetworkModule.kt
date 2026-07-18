package com.efreedom.mutter.data.di

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Supabase 접속 제공(기존 백엔드 재사용 — 키는 BuildConfig←local.properties). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSupabaseProvider(): SupabaseProvider =
        SupabaseProvider(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        )
}
