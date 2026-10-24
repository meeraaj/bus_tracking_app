package com.campus.bustracker.di

import com.campus.bustracker.data.local.DataStoreSessionRepository
import com.campus.bustracker.data.repository.AuthRepository
import com.campus.bustracker.data.repository.BusTrackingRepository
import com.campus.bustracker.data.repository.SessionRepository
import com.campus.bustracker.data.repository.SmartAuthRepository
import com.campus.bustracker.data.repository.SmartBusTrackingRepository
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
    abstract fun bindBusTrackingRepository(
        impl: SmartBusTrackingRepository
    ): BusTrackingRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: SmartAuthRepository
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: DataStoreSessionRepository
    ): SessionRepository
}
