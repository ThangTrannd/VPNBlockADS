package com.vpnblockads.core.dns.packet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChecksumTest {
    @Test
    fun `ipv4 header checksum matches known vector`() {
        // Ví dụ kinh điển (Wikipedia "IPv4 header checksum"): kết quả 0xB861.
        val header = hex("45000073000040004011 0000 c0a80001c0a800c7")
        assertThat(Checksum.ipv4Header(header, 0, 20)).isEqualTo(0xB861)
    }

    @Test
    fun `odd length data is padded with zero`() {
        val even = Checksum.finish(Checksum.sum(byteArrayOf(0x12, 0x34, 0x56, 0x00), 0, 4))
        val odd = Checksum.finish(Checksum.sum(byteArrayOf(0x12, 0x34, 0x56), 0, 3))
        assertThat(odd).isEqualTo(even)
    }

    companion object {
        fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
