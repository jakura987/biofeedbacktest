package com.intellizon.biofeedbacktest.module

import com.intellizon.biofeedbacktest.wifi.connect.TcpServerController
import com.intellizon.biofeedbacktest.wifi.manager.WifiTcpServerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TcpServerModule {

    @Binds
    @Singleton
    abstract fun bindTcpServerController(
        impl: WifiTcpServerManager
    ): TcpServerController
}