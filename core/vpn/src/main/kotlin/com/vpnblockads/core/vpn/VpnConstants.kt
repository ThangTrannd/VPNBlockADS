package com.vpnblockads.core.vpn

internal object VpnConstants {
    /**
     * Địa chỉ của TUN và DNS server "giả". Chọn dải 10.111.222.0/24 vì hiếm khi
     * trùng với mạng LAN thật (nếu trùng, gói tới LAN đó sẽ bị hút vào VPN).
     */
    const val TUN_ADDRESS = "10.111.222.1"
    const val TUN_PREFIX = 24
    const val FAKE_DNS = "10.111.222.2"

    const val MTU = 1500
    const val UPSTREAM_PORT = 53

    /** Số truy vấn được xử lý đồng thời; vượt quá thì bỏ gói (app sẽ tự hỏi lại). */
    const val MAX_IN_FLIGHT = 64

    /** Thời gian tối đa một truy vấn chờ blocklist load xong trước khi được cho qua. */
    const val FILTER_WAIT_MILLIS = 10_000L

    const val LOG_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
}
