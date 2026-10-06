package com.vpnblockads.core.model

/** Kết quả xử lý một truy vấn DNS. */
enum class QueryStatus {
    /** Không nằm trong blocklist, đã forward lên upstream. */
    ALLOWED,

    /** Trả lời từ cache, không cần hỏi upstream. */
    CACHED,

    /** Nằm trong whitelist của người dùng (ưu tiên hơn blocklist). */
    WHITELISTED,

    /** Bị chặn. */
    BLOCKED,

    /** Upstream lỗi/timeout -> đã trả SERVFAIL. */
    FAILED;

    val isBlocked: Boolean get() = this == BLOCKED
}

data class QueryLogEntry(
    val id: Long = 0,
    val domain: String,
    /** Mã loại bản ghi DNS: 1 = A, 28 = AAAA, 65 = HTTPS... */
    val type: Int,
    val timestampMillis: Long,
    val status: QueryStatus,
    val latencyMillis: Long,
)

object DnsRecordTypes {
    fun name(type: Int): String = when (type) {
        1 -> "A"
        2 -> "NS"
        5 -> "CNAME"
        6 -> "SOA"
        12 -> "PTR"
        15 -> "MX"
        16 -> "TXT"
        28 -> "AAAA"
        33 -> "SRV"
        64 -> "SVCB"
        65 -> "HTTPS"
        else -> "TYPE$type"
    }
}
