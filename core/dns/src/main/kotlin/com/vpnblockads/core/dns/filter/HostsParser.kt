package com.vpnblockads.core.dns.filter

import com.vpnblockads.core.model.Domains
import java.io.BufferedReader

/**
 * Đọc file hosts (định dạng StevenBlack: "0.0.0.0 ads.example.com") hoặc danh sách
 * mỗi dòng một domain.
 *
 * Tách token bằng tay (không Regex, không split) vì hàm chạy cho ~100k dòng mỗi lần
 * khởi động: trong lúc parse, bộ lọc chưa sẵn sàng.
 */
object HostsParser {
    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )

    fun parse(reader: BufferedReader, into: MutableSet<String> = HashSet(200_000)): MutableSet<String> {
        while (true) {
            val line = reader.readLine() ?: break
            forEachDomain(line) { into.add(it) }
        }
        return into
    }

    fun parseLine(line: String): List<String> = buildList { forEachDomain(line) { add(it) } }

    private inline fun forEachDomain(line: String, action: (String) -> Unit) {
        val end = line.indexOf('#').let { if (it < 0) line.length else it }
        var tokenIndex = 0
        var firstIsIp = false
        var i = 0
        while (i < end) {
            while (i < end && line[i].isWhitespace()) i++
            if (i >= end) break
            val start = i
            while (i < end && !line[i].isWhitespace()) i++
            val token = line.substring(start, i)
            if (tokenIndex == 0) {
                firstIsIp = looksLikeIp(token)
                if (!firstIsIp) {
                    accept(token)?.let(action)
                    return // định dạng "mỗi dòng một domain": chỉ lấy token đầu
                }
            } else {
                accept(token)?.let(action)
            }
            tokenIndex++
        }
    }

    private fun accept(raw: String): String? =
        Domains.normalize(raw).takeIf { it !in IGNORED && Domains.isValid(it) }

    private fun looksLikeIp(token: String): Boolean =
        token.indexOf(':') >= 0 || token.all { it.isDigit() || it == '.' }
}
