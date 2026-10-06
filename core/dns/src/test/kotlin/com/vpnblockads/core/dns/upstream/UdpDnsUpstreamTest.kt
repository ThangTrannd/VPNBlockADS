package com.vpnblockads.core.dns.upstream

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.dns.TestDns
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.xbill.DNS.Message
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Dùng một "DNS server" UDP thật chạy trên localhost. */
class UdpDnsUpstreamTest {
    private val server = DatagramSocket(0, InetAddress.getLoopbackAddress())
    private val received = AtomicInteger()

    private fun startServer(dropFirst: Int, respond: Boolean = true) = thread(isDaemon = true) {
        val buffer = ByteArray(512)
        try {
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                server.receive(packet)
                val count = received.incrementAndGet()
                if (!respond || count <= dropFirst) continue
                val query = Message(buffer.copyOf(packet.length))
                val response = TestDns.aResponse("example.com", id = query.header.id)
                server.send(DatagramPacket(response, response.size, packet.socketAddress))
            }
        } catch (_: Exception) {
        }
    }

    private fun upstream(timeout: Long = 2_000, retransmit: Long = 200) = UdpDnsUpstream(
        serverProvider = { InetSocketAddress(server.localAddress, server.localPort) },
        protector = { true },
        timeoutMillis = timeout,
        retransmitMillis = retransmit,
    )

    @After
    fun tearDown() = server.close()

    @Test
    fun `returns matching response`() = runBlocking {
        startServer(dropFirst = 0)
        val response = upstream().resolve(TestDns.queryBytes("example.com", id = 0x0102))
        assertThat(Message(response).header.id).isEqualTo(0x0102)
    }

    @Test
    fun `retransmits when first packet is lost`() = runBlocking {
        startServer(dropFirst = 1)
        val response = upstream().resolve(TestDns.queryBytes("example.com", id = 7))
        assertThat(Message(response).header.id).isEqualTo(7)
        assertThat(received.get()).isAtLeast(2)
    }

    @Test(expected = SocketTimeoutException::class)
    fun `times out when server never answers`(): Unit = runBlocking {
        startServer(dropFirst = 0, respond = false)
        upstream(timeout = 500, retransmit = 200).resolve(TestDns.queryBytes("example.com"))
    }

    @Test(expected = java.io.IOException::class)
    fun `fails when socket cannot be protected`(): Unit = runBlocking {
        UdpDnsUpstream({ InetSocketAddress(server.localAddress, server.localPort) }, { false })
            .resolve(TestDns.queryBytes("example.com"))
    }
}
