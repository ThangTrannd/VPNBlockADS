package com.vpnblockads.core.dns

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.dns.cache.DnsCache
import com.vpnblockads.core.dns.filter.DomainFilter
import com.vpnblockads.core.dns.packet.ParseResult
import com.vpnblockads.core.dns.packet.PacketParser
import com.vpnblockads.core.dns.upstream.DnsUpstream
import com.vpnblockads.core.model.BlockResponseMode
import com.vpnblockads.core.model.QueryLogEntry
import com.vpnblockads.core.model.QueryStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import java.io.IOException

class DnsRequestHandlerTest {
    private val upstream = mockk<DnsUpstream>()
    private val logs = mutableListOf<QueryLogEntry>()
    private val filter = DomainFilter(blocked = listOf(setOf("ads.com")), allowed = setOf("ok.ads.com"))

    private fun handler(cache: DnsCache? = null) = DnsRequestHandler(
        filterProvider = { filter },
        blockModeProvider = { BlockResponseMode.NXDOMAIN },
        upstream = upstream,
        cache = cache,
        upstreamTimeoutMillis = 5_000,
        onQuery = { logs += it },
    )

    @Test
    fun `blocked domain gets nxdomain without asking upstream`() = runTest {
        val response = handler().resolve(TestDns.queryBytes("x.ads.com"))!!

        assertThat(Message(response).rcode).isEqualTo(Rcode.NXDOMAIN)
        coVerify(exactly = 0) { upstream.resolve(any()) }
        assertThat(logs.single().status).isEqualTo(QueryStatus.BLOCKED)
        assertThat(logs.single().domain).isEqualTo("x.ads.com")
    }

    @Test
    fun `allowed and whitelisted domains are forwarded`() = runTest {
        coEvery { upstream.resolve(any()) } answers { TestDns.aResponse("example.com") }

        handler().resolve(TestDns.queryBytes("example.com"))
        handler().resolve(TestDns.queryBytes("ok.ads.com"))

        coVerify(exactly = 2) { upstream.resolve(any()) }
        assertThat(logs.map { it.status }).containsExactly(QueryStatus.ALLOWED, QueryStatus.WHITELISTED).inOrder()
    }

    @Test
    fun `slow upstream times out with servfail`() = runTest {
        coEvery { upstream.resolve(any()) } coAnswers {
            delay(60_000)
            TestDns.aResponse("slow.com")
        }

        val response = handler().resolve(TestDns.queryBytes("slow.com"))!!

        assertThat(Message(response).rcode).isEqualTo(Rcode.SERVFAIL)
        assertThat(logs.single().status).isEqualTo(QueryStatus.FAILED)
    }

    @Test
    fun `upstream error returns servfail`() = runTest {
        coEvery { upstream.resolve(any()) } throws IOException("network down")
        val response = handler().resolve(TestDns.queryBytes("example.com"))!!
        assertThat(Message(response).rcode).isEqualTo(Rcode.SERVFAIL)
    }

    @Test
    fun `second query is served from cache`() = runTest {
        coEvery { upstream.resolve(any()) } answers { TestDns.aResponse("example.com") }
        val h = handler(DnsCache())

        h.resolve(TestDns.queryBytes("example.com", id = 1))
        val second = h.resolve(TestDns.queryBytes("example.com", id = 2))!!

        coVerify(exactly = 1) { upstream.resolve(any()) }
        assertThat(Message(second).header.id).isEqualTo(2)
        assertThat(logs.last().status).isEqualTo(QueryStatus.CACHED)
    }

    @Test
    fun `malformed payload is dropped`() = runTest {
        assertThat(handler().resolve(byteArrayOf(1, 2, 3))).isNull()
    }

    @Test
    fun `handle wraps response into ip packet back to the app`() = runTest {
        val packet = TestDns.packet(TestDns.queryBytes("ads.com"), sourcePort = 51515)

        val ip = handler().handle(packet)!!

        // Parse lại như thể là gói đi theo chiều ngược: kiểm tra đích là app.
        assertThat(ip.copyOfRange(16, 20)).isEqualTo(TestDns.APP_ADDRESS)
        val dns = ip.copyOfRange(28, ip.size)
        assertThat(Message(dns).rcode).isEqualTo(Rcode.NXDOMAIN)
        assertThat(PacketParser.parse(ip, ip.size)).isInstanceOf(ParseResult.Ignored::class.java) // src port 53, không phải truy vấn
    }
}
