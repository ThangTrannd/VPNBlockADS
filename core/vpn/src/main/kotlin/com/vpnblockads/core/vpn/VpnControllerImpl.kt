package com.vpnblockads.core.vpn

import android.content.Context
import androidx.core.content.ContextCompat
import com.vpnblockads.core.domain.repository.VpnController
import com.vpnblockads.core.model.VpnStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cầu nối UI <-> service. Service và UI chạy cùng process nên chia sẻ trạng thái
 * qua một singleton StateFlow là đủ (không cần bind service hay broadcast).
 */
@Singleton
class VpnControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : VpnController {
    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Stopped)
    override val status: StateFlow<VpnStatus> = _status.asStateFlow()

    internal fun update(status: VpnStatus) {
        _status.value = status
    }

    override fun start() {
        ContextCompat.startForegroundService(context, AdBlockVpnService.startIntent(context))
    }

    override fun stop() {
        context.startService(AdBlockVpnService.stopIntent(context))
    }
}
