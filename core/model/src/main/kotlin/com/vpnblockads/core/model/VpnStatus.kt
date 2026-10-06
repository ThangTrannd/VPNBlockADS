package com.vpnblockads.core.model

sealed interface VpnStatus {
    data object Stopped : VpnStatus
    data object Starting : VpnStatus
    data class Running(val sinceMillis: Long) : VpnStatus
    data class Error(val message: String) : VpnStatus
}

data class BlocklistStatus(
    val domainCount: Int = 0,
    val isLoading: Boolean = false,
    /** null = đang dùng file đóng gói trong assets. */
    val lastUpdatedMillis: Long? = null,
)
