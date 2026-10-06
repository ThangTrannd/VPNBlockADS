package com.vpnblockads.core.dns.packet

object IpConstants {
    const val IPV4_HEADER_MIN = 20
    const val UDP_HEADER_SIZE = 8
    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17
    const val DNS_PORT = 53
    const val DEFAULT_TTL = 64

    /** Cờ "More Fragments" và mặt nạ fragment offset trong header IPv4. */
    const val FLAG_MORE_FRAGMENTS = 0x2000
    const val FRAGMENT_OFFSET_MASK = 0x1FFF
    const val FLAG_DONT_FRAGMENT = 0x4000
}

internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

internal fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int): Long =
    (u16(offset).toLong() shl 16) or u16(offset + 2).toLong()

internal fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

internal fun ByteArray.putU32(offset: Int, value: Long) {
    putU16(offset, (value ushr 16).toInt() and 0xFFFF)
    putU16(offset + 2, value.toInt() and 0xFFFF)
}
