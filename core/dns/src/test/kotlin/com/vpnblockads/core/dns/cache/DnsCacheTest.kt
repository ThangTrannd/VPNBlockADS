package com.vpnblockads.core.dns.cache

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.dns.TestDns
import org.junit.Test
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import org.xbill.DNS.Type

class DnsCacheTest {
    private var now = 1_000_000L
    private val cache = DnsCache(maxEntries = 3, maxTtlSeconds = 3600, clock = { now })

    @Test
    fun `hit rewrites id and decrements ttl`() {
        cache.put(TestDns.query("example.com", id = 1), TestDns.aResponse("example.com", ttl = 300, id = 1))
        now += 100_000

        val cached = Message(cache.get(TestDns.query("example.com", id = 777))!!)

        assertThat(cached.header.id).isEqualTo(777)
        assertThat(cached.getSection(Section.ANSWER).single().ttl).isEqualTo(200)
    }

    @Test
    fun `entry expires after ttl`() {
        cache.put(TestDns.query("example.com"), TestDns.aResponse("example.com", ttl = 60))
        now += 60_000
        assertThat(cache.get(TestDns.query("example.com"))).isNull()
    }

    @Test
    fun `key includes record type`() {
        cache.put(TestDns.query("example.com", Type.A), TestDns.aResponse("example.com"))
        assertThat(cache.get(TestDns.query("example.com", Type.AAAA))).isNull()
    }

    @Test
    fun `ttl is capped`() {
        cache.put(TestDns.query("example.com"), TestDns.aResponse("example.com", ttl = 86_400))
        now += 3_600_000
        assertThat(cache.get(TestDns.query("example.com"))).isNull()
    }

    @Test
    fun `nxdomain cached using soa minimum`() {
        val response = TestDns.nxDomainWithSoa("nope.example.com", soaTtl = 900, soaMinimum = 120)
        cache.put(TestDns.query("nope.example.com"), response)

        now += 119_000
        assertThat(Message(cache.get(TestDns.query("nope.example.com"))!!).rcode).isEqualTo(Rcode.NXDOMAIN)
        now += 1_000
        assertThat(cache.get(TestDns.query("nope.example.com"))).isNull()
    }

    @Test
    fun `servfail and truncated responses are not cached`() {
        cache.put(TestDns.query("fail.com"), TestDns.rcodeResponse("fail.com", Rcode.SERVFAIL))
        assertThat(cache.get(TestDns.query("fail.com"))).isNull()

        val truncated = Message(TestDns.aResponse("big.com")).apply { header.setFlag(Flags.TC.toInt()) }.toWire()
        cache.put(TestDns.query("big.com"), truncated)
        assertThat(cache.get(TestDns.query("big.com"))).isNull()
    }

    @Test
    fun `evicts least recently used entry`() {
        listOf("a.com", "b.com", "c.com").forEach { cache.put(TestDns.query(it), TestDns.aResponse(it)) }
        cache.get(TestDns.query("a.com")) // a vừa được dùng
        cache.put(TestDns.query("d.com"), TestDns.aResponse("d.com"))

        assertThat(cache.get(TestDns.query("b.com"))).isNull()
        assertThat(cache.get(TestDns.query("a.com"))).isNotNull()
        assertThat(cache.size).isEqualTo(3)
    }
}

class DnsCacheQuestionCaseTest {
    @Test
    fun `cached response echoes question exactly as the new query wrote it`() {
        val cache = DnsCache(clock = { 0L })
        cache.put(TestDns.query("github.com"), TestDns.aResponse("github.com"))

        val mixedCase = TestDns.query("GitHub.COM", id = 9)
        val cached = cache.get(mixedCase)!!

        // Question section (từ byte 12 tới hết question) phải giống hệt truy vấn mới.
        assertThat(cached.copyOfRange(12, mixedCase.questionEnd))
            .isEqualTo(mixedCase.raw.copyOfRange(12, mixedCase.questionEnd))
        assertThat(Message(cached).getSection(Section.ANSWER)).hasSize(1)
    }
}
