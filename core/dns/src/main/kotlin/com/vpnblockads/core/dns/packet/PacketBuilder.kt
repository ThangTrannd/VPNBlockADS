package com.vpnblockads.core.dns.packet

/**
 * Đóng gói câu trả lời DNS thành gói IP/UDP để ghi ngược vào TUN.
 * Nguồn/đích bị đảo so với truy vấn: câu trả lời "đến từ" DNS server giả,
 * gửi về đúng app (IP + port) đã hỏi.
 */
object PacketBuilder {

    fun buildResponse(query: DnsQueryPacket, dnsPayload: ByteArray): ByteArray = when (query.ipVersion) {
        4 -> buildIpv4(query, dnsPayload)
        else -> throw UnsupportedOperationException("IPv${query.ipVersion} not supported")
    }

    private fun buildIpv4(query: DnsQueryPacket, dnsPayload: ByteArray): ByteArray {
        val headerLength = IpConstants.IPV4_HEADER_MIN
        val udpLength = IpConstants.UDP_HEADER_SIZE + dnsPayload.size
        val totalLength = headerLength + udpLength
        require(totalLength <= 0xFFFF) { "payload too large: ${dnsPayload.size}" }

        val out = ByteArray(totalLength)
        // --- IPv4 header ---
        out[0] = 0x45 // version 4, IHL 5 (20 byte, không options)
        out[1] = 0 // TOS
        out.putU16(2, totalLength)
        out.putU16(4, 0) // identification: không phân mảnh nên không cần
        out.putU16(6, IpConstants.FLAG_DONT_FRAGMENT)
        out[8] = IpConstants.DEFAULT_TTL.toByte()
        out[9] = IpConstants.PROTOCOL_UDP.toByte()
        // byte 10-11: checksum, để 0 rồi tính sau
        query.destinationAddress.copyInto(out, 12) // src = DNS server giả
        query.sourceAddress.copyInto(out, 16) // dst = app đã hỏi
        out.putU16(10, Checksum.ipv4Header(out, 0, headerLength))

        // --- UDP header ---
        val udp = headerLength
        out.putU16(udp, query.destinationPort) // src port = 53
        out.putU16(udp + 2, query.sourcePort)
        out.putU16(udp + 4, udpLength)
        // udp + 6: checksum, tính sau khi chép payload
        dnsPayload.copyInto(out, udp + IpConstants.UDP_HEADER_SIZE)
        out.putU16(
            udp + 6,
            Checksum.udpIpv4(query.destinationAddress, query.sourceAddress, out, udp, udpLength),
        )
        return out
    }
}
