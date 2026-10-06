package com.vpnblockads.core.dns.packet

/**
 * Internet checksum (RFC 1071) dùng cho cả header IPv4 và UDP.
 *
 * Cách tính: cộng dồn dữ liệu theo từng từ 16-bit (big-endian), "gập" phần tràn
 * trên 16 bit về lại (one's complement sum), cuối cùng đảo bit.
 * Bên nhận cộng lại toàn bộ (kể cả trường checksum) -> kết quả đảo bit phải bằng 0.
 */
object Checksum {

    /** Tổng 16-bit chưa gập/đảo, có thể cộng nối nhiều đoạn dữ liệu. */
    fun sum(data: ByteArray, offset: Int, length: Int, initial: Long = 0): Long {
        var sum = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        // Số byte lẻ: byte cuối coi như byte cao, byte thấp = 0.
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        return sum
    }

    fun finish(sum: Long): Int {
        var s = sum
        while (s shr 16 != 0L) s = (s and 0xFFFF) + (s shr 16)
        return (s.inv() and 0xFFFF).toInt()
    }

    /** Checksum header IPv4. Trường checksum (byte 10-11) phải đang = 0 khi gọi. */
    fun ipv4Header(packet: ByteArray, offset: Int, headerLength: Int): Int =
        finish(sum(packet, offset, headerLength))

    /**
     * Checksum UDP trên IPv4: tính trên "pseudo header" (src IP, dst IP, 0, protocol=17,
     * UDP length) + toàn bộ UDP header & payload. Trường checksum UDP phải đang = 0.
     * Kết quả 0 được ghi thành 0xFFFF vì 0 nghĩa là "không dùng checksum".
     */
    fun udpIpv4(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        udp: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        var sum = sum(sourceAddress, 0, 4) + sum(destinationAddress, 0, 4)
        sum += IpConstants.PROTOCOL_UDP + udpLength
        sum = sum(udp, udpOffset, udpLength, sum)
        val result = finish(sum)
        return if (result == 0) 0xFFFF else result
    }
}
