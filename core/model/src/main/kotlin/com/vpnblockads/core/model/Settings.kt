package com.vpnblockads.core.model

/** DNS upstream mà truy vấn hợp lệ sẽ được forward tới. */
sealed interface UpstreamDns {
    val address: String

    data object Cloudflare : UpstreamDns {
        override val address = "1.1.1.1"
    }

    data object Google : UpstreamDns {
        override val address = "8.8.8.8"
    }

    data object Quad9 : UpstreamDns {
        override val address = "9.9.9.9"
    }

    data class Custom(override val address: String) : UpstreamDns

    companion object {
        val presets: List<UpstreamDns> = listOf(Cloudflare, Google, Quad9)
    }
}

/** Cách trả lời khi domain bị chặn. */
enum class BlockResponseMode {
    /** "Domain không tồn tại" (rcode 3). Mặc định. */
    NXDOMAIN,

    /** Trả 0.0.0.0 cho A, :: cho AAAA (một số app xử lý êm hơn NXDOMAIN). */
    NULL_IP,

    /** Từ chối (rcode 5). */
    REFUSED,
}

data class AppSettings(
    val upstream: UpstreamDns = UpstreamDns.Cloudflare,
    val blockMode: BlockResponseMode = BlockResponseMode.NXDOMAIN,
    val blocklistUrl: String = DEFAULT_BLOCKLIST_URL,
) {
    companion object {
        const val DEFAULT_BLOCKLIST_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    }
}
