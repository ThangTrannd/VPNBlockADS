package com.vpnblockads.core.dns.cache

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.dns.message.DnsMessage
import com.vpnblockads.core.dns.packet.ChecksumTest.Companion.hex
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.Message
import org.xbill.DNS.Section

/**
 * Byte thật bắt từ 1.1.1.1 (truy vấn A github.com có bản ghi EDNS OPT ở additional).
 * Đảm bảo parser/cache xử lý được gói "ngoài đời", không chỉ gói do dnsjava tạo.
 */
class RealTrafficFixtureTest {
    private val query = hex("abcd010000010000000000010667697468756203636f6d000001000100002904d0000000000000")
    private val response = hex(
        "abcd818000010001000000010667697468756203636f6d0000010001c00c000100010000001d000414cdf3a600002904d0000000000000",
    )

    @Test
    fun `parses real query with edns`() {
        val parsed = DnsMessage.parseQuery(query)!!
        assertThat(parsed.question.name).isEqualTo("github.com")
        assertThat(parsed.question.type).isEqualTo(1)
    }

    @Test
    fun `caches real response and ignores opt ttl`() {
        var now = 0L
        val cache = DnsCache(clock = { now })
        cache.put(DnsMessage.parseQuery(query)!!, response)
        now += 10_000

        val newQuery = query.copyOf().also { it[0] = 0x11; it[1] = 0x22 }
        val cached = Message(cache.get(DnsMessage.parseQuery(newQuery)!!)!!)

        assertThat(cached.header.id).isEqualTo(0x1122)
        val a = cached.getSection(Section.ANSWER).single() as ARecord
        assertThat(a.ttl).isEqualTo(0x1d - 10L)
        assertThat(a.address.hostAddress).isEqualTo("20.205.243.166")
        assertThat(cached.opt.payloadSize).isEqualTo(1232) // OPT giữ nguyên
    }
}
