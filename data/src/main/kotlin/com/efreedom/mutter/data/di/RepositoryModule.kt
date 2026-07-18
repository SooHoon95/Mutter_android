package com.efreedom.mutter.data.di

import com.efreedom.mutter.data.repository.AuthRepositoryImpl
import com.efreedom.mutter.data.repository.ConnectionRepositoryImpl
import com.efreedom.mutter.data.repository.DeliveryRepositoryImpl
import com.efreedom.mutter.data.repository.InboxRepositoryImpl
import com.efreedom.mutter.data.repository.LetterRepositoryImpl
import com.efreedom.mutter.data.repository.ProfileRepositoryImpl
import com.efreedom.mutter.data.repository.PushTokenRepositoryImpl
import com.efreedom.mutter.data.repository.ReceiptRepositoryImpl
import com.efreedom.mutter.data.repository.SoundCloudRepositoryImpl
import com.efreedom.mutter.data.repository.TakedownRepositoryImpl
import com.efreedom.mutter.data.repository.ThreadRepositoryImpl
import com.efreedom.mutter.data.service.SessionProviderImpl
import com.efreedom.mutter.domain.repository.AuthRepository
import com.efreedom.mutter.domain.repository.ConnectionRepository
import com.efreedom.mutter.domain.repository.DeliveryRepository
import com.efreedom.mutter.domain.repository.InboxRepository
import com.efreedom.mutter.domain.repository.LetterRepository
import com.efreedom.mutter.domain.repository.ProfileRepository
import com.efreedom.mutter.domain.repository.PushTokenRepository
import com.efreedom.mutter.domain.repository.ReceiptRepository
import com.efreedom.mutter.domain.repository.SessionProvider
import com.efreedom.mutter.domain.repository.SoundCloudRepository
import com.efreedom.mutter.domain.repository.TakedownRepository
import com.efreedom.mutter.domain.repository.ThreadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Repository 인터페이스 → 구현 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindSessionProvider(impl: SessionProviderImpl): SessionProvider

    @Binds @Singleton
    abstract fun bindLetterRepository(impl: LetterRepositoryImpl): LetterRepository

    @Binds @Singleton
    abstract fun bindDeliveryRepository(impl: DeliveryRepositoryImpl): DeliveryRepository

    @Binds @Singleton
    abstract fun bindInboxRepository(impl: InboxRepositoryImpl): InboxRepository

    @Binds @Singleton
    abstract fun bindThreadRepository(impl: ThreadRepositoryImpl): ThreadRepository

    @Binds @Singleton
    abstract fun bindReceiptRepository(impl: ReceiptRepositoryImpl): ReceiptRepository

    @Binds @Singleton
    abstract fun bindConnectionRepository(impl: ConnectionRepositoryImpl): ConnectionRepository

    @Binds @Singleton
    abstract fun bindTakedownRepository(impl: TakedownRepositoryImpl): TakedownRepository

    @Binds @Singleton
    abstract fun bindPushTokenRepository(impl: PushTokenRepositoryImpl): PushTokenRepository

    @Binds @Singleton
    abstract fun bindSoundCloudRepository(impl: SoundCloudRepositoryImpl): SoundCloudRepository
}
