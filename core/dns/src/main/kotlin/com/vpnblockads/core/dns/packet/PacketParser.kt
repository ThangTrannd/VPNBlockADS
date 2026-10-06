package com.vpnblockads.core.dns.packet

/**
 * Một truy vấn DNS đọc được từ TUN, đã bóc lớp IP + UDP.
 * Giữ lại địa chỉ/port để lát nữa đóng gói câu trả lời (đảo chiều).
 *
 * [ipVersion] để sẵn cho IPv6 sau này (địa chỉ khi đó dài 16 byte).
 */
class DnsQueryPacket(
    val ipVersion: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val dnsPayload: ByteArray,
)

sealed interface ParseResult {
    class Dns(val packet: DnsQueryPacket) : ParseResult

    /** Gói không thuộc diện xử lý. Không phải lỗi: chỉ cần bỏ qua. */
    data class Ignored(val reason: String) : ParseResult
}

/**
 * Bóc gói IPv4 -> UDP -> payload DNS.
 *
 * Header IPv4 (tối thiểu 20 byte):
 * ```
 *  0      4      8              16                              31
 * |Version| IHL  |    TOS       |          Total Length          |
 * |       Identification        |Flags|    Fragment Offset       |
 * |    TTL       |  Protocol    |        Header Checksum         |
 * |                     Source Address                           |
 * |                   Destination Address                        |
 * |              Options (nếu IHL > 5) ...                       |
 * ```
 * Header UDP (8 byte): src port, dst port, length (header + data), checksum.
 *
 * Không bao giờ throw: gói hỏng/lạ -> [ParseResult.Ignored].
 */
object PacketParser {

    fun parse(buffer: ByteArray, length: Int): ParseResult {
        if (length < 1) return ParseResult.Ignored("empty")
        return when (val version = buffer.u8(0) ushr 4) {
            4 -> parseIpv4(buffer, length)
            // Mở rộng sau: parseIpv6(...) — header cố định 40 byte, có extension headers.
            6 -> ParseResult.Ignored("ipv6 not supported")
            else -> ParseResult.Ignored("unknown ip version $version")
        }
    }

    private fun parseIpv4(buffer: ByteArray, length: Int): ParseResult {
        if (length < IpConstants.IPV4_HEADER_MIN) return ParseResult.Ignored("short ipv4 header")
        val headerLength = (buffer.u8(0) and 0x0F) * 4
        if (headerLength < IpConstants.IPV4_HEADER_MIN || headerLength > length) {
            return ParseResult.Ignored("bad ihl")
        }
        val totalLength = buffer.u16(2)
        if (totalLength < headerLength || totalLength > length) {
            return ParseResult.Ignored("bad total length")
        }
        val flagsAndOffset = buffer.u16(6)
        if (flagsAndOffset and IpConstants.FLAG_MORE_FRAGMENTS != 0 ||
            flagsAndOffset and IpConstants.FRAGMENT_OFFSET_MASK != 0
        ) {
            return ParseResult.Ignored("fragmented")
        }
        when (val protocol = buffer.u8(9)) {
            IpConstants.PROTOCOL_UDP -> Unit
            // Mở rộng sau: DNS qua TCP (thường khi câu trả lời > 512 byte và bị cờ TC).
            IpConstants.PROTOCOL_TCP -> return ParseResult.Ignored("tcp not supported")
            else -> return ParseResult.Ignored("protocol $protocol")
        }
        if (totalLength < headerLength + IpConstants.UDP_HEADER_SIZE) {
            return ParseResult.Ignored("short udp header")
        }

        val udp = headerLength
        val sourcePort = buffer.u16(udp)
        val destinationPort = buffer.u16(udp + 2)
        val udpLength = buffer.u16(udp + 4)
        if (destinationPort != IpConstants.DNS_PORT) return ParseResult.Ignored("port $destinationPort")
        if (udpLength < IpConstants.UDP_HEADER_SIZE || udp + udpLength > totalLength) {
            return ParseResult.Ignored("bad udp length")
        }

        return ParseResult.Dns(
            DnsQueryPacket(
                ipVersion = 4,
                sourceAddress = buffer.copyOfRange(12, 16),
                destinationAddress = buffer.copyOfRange(16, 20),
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                dnsPayload = buffer.copyOfRange(udp + IpConstants.UDP_HEADER_SIZE, udp + udpLength),
            ),
        )
    }
}
