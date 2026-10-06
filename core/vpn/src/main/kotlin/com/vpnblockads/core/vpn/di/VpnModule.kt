package com.vpnblockads.core.vpn.di

import com.vpnblockads.core.domain.repository.VpnController
import com.vpnblockads.core.vpn.VpnControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {
    @Binds abstract fun vpnController(impl: VpnControllerImpl): VpnController
}
