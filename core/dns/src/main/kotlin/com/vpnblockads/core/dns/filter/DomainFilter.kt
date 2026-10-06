package com.vpnblockads.core.dns.filter

import com.vpnblockads.core.model.Domains

enum class FilterDecision { ALLOW, ALLOW_WHITELISTED, BLOCK }

/**
 * Snapshot bất biến của bộ lọc. Khi danh sách đổi thì tạo snapshot mới và thay
 * nguyên tham chiếu (không sửa tại chỗ) -> luồng xử lý DNS đọc không cần khoá.
 *
 * [blocked] là danh sách nhiều Set để không phải gộp (copy) blocklist ~100k domain
 * mỗi khi người dùng thêm một quy tắc.
 *
 * Quy tắc khớp: domain khớp nếu chính nó hoặc một domain cha nằm trong danh sách.
 * "a.b.ads.com" kiểm tra "a.b.ads.com" -> "b.ads.com" -> "ads.com" (không xét TLD "com").
 * Whitelist luôn thắng blocklist.
 */
class DomainFilter(
    private val blocked: List<Set<String>>,
    private val allowed: Set<String>,
) {
    fun decide(domain: String): FilterDecision {
        val name = Domains.normalize(domain)
        if (name.isEmpty()) return FilterDecision.ALLOW
        return when {
            matches(name) { it in allowed } -> FilterDecision.ALLOW_WHITELISTED
            matches(name) { candidate -> blocked.any { candidate in it } } -> FilterDecision.BLOCK
            else -> FilterDecision.ALLOW
        }
    }

    private inline fun matches(domain: String, contains: (String) -> Boolean): Boolean {
        var candidate = domain
        while (true) {
            if (contains(candidate)) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
            // Dừng trước TLD: không bao giờ chặn cả ".com".
            if (candidate.indexOf('.') < 0) return false
        }
    }

    companion object {
        val EMPTY = DomainFilter(emptyList(), emptySet())
    }
}
