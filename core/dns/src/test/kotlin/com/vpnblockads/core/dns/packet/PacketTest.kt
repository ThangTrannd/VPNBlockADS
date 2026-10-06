package com.vpnblockads.core.dns.packet

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.dns.TestDns
import org.junit.Test

class PacketTest {
    private val payload = TestDns.queryBytes("example.com")

    /** Gói "app gửi tới DNS giả" — tạo bằng builder rồi đảo lại vai trò. */
    private fun requestPacket(): ByteArray {
        val reverse = TestDns.packet(payload).let {
            DnsQueryPacket(4, it.destinationAddress, it.sourceAddress, 53, it.sourcePort, payload)
        }
        // buildResponse đảo src/dst nên kết quả chính là gói app -> 10.111.222.2:53
        return PacketBuilder.buildResponse(reverse, payload)
    }

    @Test
    fun `parses ipv4 udp dns packet`() {
        val bytes = requestPacket()
        val result = PacketParser.parse(bytes, bytes.size)

        assertThat(result).isInstanceOf(ParseResult.Dns::class.java)
        val packet = (result as ParseResult.Dns).packet
        assertThat(packet.sourceAddress).isEqualTo(TestDns.APP_ADDRESS)
        assertThat(packet.destinationAddress).isEqualTo(TestDns.FAKE_DNS)
        assertThat(packet.sourcePort).isEqualTo(40000)
        assertThat(packet.destinationPort).isEqualTo(53)
        assertThat(packet.dnsPayload).isEqualTo(payload)
    }

    @Test
    fun `parse respects length argument instead of buffer size`() {
        val bytes = requestPacket()
        val buffer = bytes.copyOf(32767) // giống buffer đọc từ TUN: lớn hơn gói thật
        val result = PacketParser.parse(buffer, bytes.size) as ParseResult.Dns
        assertThat(result.packet.dnsPayload).isEqualTo(payload)
    }

    @Test
    fun `handles ipv4 header with options`() {
        val plain = requestPacket()
        // Chèn 4 byte option (NOP x4) -> IHL = 6
        val withOptions = ByteArray(plain.size + 4)
        plain.copyInto(withOptions, 0, 0, 20)
        withOptions[0] = 0x46
        for (i in 20 until 24) withOptions[i] = 1
        plain.copyInto(withOptions, 24, 20)
        withOptions.putU16(2, withOptions.size)

        val result = PacketParser.parse(withOptions, withOptions.size) as ParseResult.Dns
        assertThat(result.packet.dnsPayload).isEqualTo(payload)
    }

    @Test
    fun `response swaps addresses and ports`() {
        val query = TestDns.packet(payload)
        val response = PacketBuilder.buildResponse(query, byteArrayOf(1, 2, 3))

        assertThat(response.copyOfRange(12, 16)).isEqualTo(TestDns.FAKE_DNS)
        assertThat(response.copyOfRange(16, 20)).isEqualTo(TestDns.APP_ADDRESS)
        assertThat(response.u16(20)).isEqualTo(53)
        assertThat(response.u16(22)).isEqualTo(40000)
    }

    @Test
    fun `response has correct lengths`() {
        val dns = ByteArray(45) { it.toByte() }
        val response = PacketBuilder.buildResponse(TestDns.packet(payload), dns)

        assertThat(response.size).isEqualTo(20 + 8 + 45)
        assertThat(response.u16(2)).isEqualTo(response.size) // IP total length
        assertThat(response.u16(24)).isEqualTo(8 + 45) // UDP length
        assertThat(response.u8(0)).isEqualTo(0x45)
        assertThat(response.u8(9)).isEqualTo(IpConstants.PROTOCOL_UDP)
        assertThat(response.copyOfRange(28, response.size)).isEqualTo(dns)
    }

    @Test
    fun `response ip and udp checksums verify`() {
        val response = PacketBuilder.buildResponse(TestDns.packet(payload), TestDns.aResponse("example.com"))

        // Bên nhận cộng cả trường checksum: kết quả sau khi gập & đảo phải = 0.
        assertThat(Checksum.finish(Checksum.sum(response, 0, 20))).isEqualTo(0)

        val udpLength = response.size - 20
        var sum = Checksum.sum(response, 12, 8) // src + dst IP
        sum += IpConstants.PROTOCOL_UDP + udpLength
        sum = Checksum.sum(response, 20, udpLength, sum)
        assertThat(Checksum.finish(sum)).isEqualTo(0)
    }

    @Test
    fun `round trip build then parse`() {
        val bytes = requestPacket()
        val parsed = (PacketParser.parse(bytes, bytes.size) as ParseResult.Dns).packet
        val response = PacketBuilder.buildResponse(parsed, payload)
        assertThat(response.copyOfRange(12, 20))
            .isEqualTo(TestDns.FAKE_DNS + TestDns.APP_ADDRESS)
    }

    @Test
    fun `ignores non dns and malformed packets without throwing`() {
        val bytes = requestPacket()

        val tcp = bytes.copyOf().also { it[9] = IpConstants.PROTOCOL_TCP.toByte() }
        val otherPort = bytes.copyOf().also { it.putU16(22, 443) }
        val ipv6 = bytes.copyOf().also { it[0] = 0x60 }
        val fragmented = bytes.copyOf().also { it.putU16(6, IpConstants.FLAG_MORE_FRAGMENTS) }
        val badUdpLength = bytes.copyOf().also { it.putU16(24, 5000) }

        listOf(tcp, otherPort, ipv6, fragmented, badUdpLength).forEach {
            assertThat(PacketParser.parse(it, it.size)).isInstanceOf(ParseResult.Ignored::class.java)
        }
        assertThat(PacketParser.parse(bytes, 10)).isInstanceOf(ParseResult.Ignored::class.java)
        assertThat(PacketParser.parse(ByteArray(0), 0)).isInstanceOf(ParseResult.Ignored::class.java)
        assertThat(PacketParser.parse(ByteArray(64) { -1 }, 64)).isInstanceOf(ParseResult.Ignored::class.java)
    }
}
