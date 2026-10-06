package com.vpnblockads.core.dns.filter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HostsParserTest {
    @Test
    fun `parses stevenblack style hosts file`() {
        val hosts = """
            # Title: StevenBlack/hosts
            127.0.0.1 localhost
            127.0.0.1 localhost.localdomain
            255.255.255.255 broadcasthost
            ::1 localhost
            0.0.0.0 0.0.0.0
            
            0.0.0.0 Ads.Example.com   # comment
            0.0.0.0	tab.separated.net
            0.0.0.0 a.com b.com
            plain-domain.org
            0.0.0.0 not_valid!.com
        """.trimIndent()

        val result = HostsParser.parse(hosts.reader().buffered())

        assertThat(result).containsExactly(
            "ads.example.com", "tab.separated.net", "a.com", "b.com", "plain-domain.org",
        )
    }

    @Test
    fun `comment only and blank lines produce nothing`() {
        assertThat(HostsParser.parseLine("   # 0.0.0.0 ads.com")).isEmpty()
        assertThat(HostsParser.parseLine("")).isEmpty()
    }
}
