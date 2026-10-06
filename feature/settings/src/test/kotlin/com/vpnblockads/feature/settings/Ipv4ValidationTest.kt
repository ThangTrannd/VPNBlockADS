package com.vpnblockads.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Ipv4ValidationTest {
    @Test
    fun `accepts valid ipv4`() {
        listOf("1.1.1.1", "8.8.8.8", "94.140.14.14", "255.255.255.255", "0.0.0.0").forEach {
            assertThat(SettingsViewModel.isValidIpv4(it)).isTrue()
        }
    }

    @Test
    fun `rejects hostnames and malformed addresses`() {
        listOf("", "dns.google", "1.1.1", "256.1.1.1", "1.1.1.1.1", "01.1.1.1 ", "::1").forEach {
            assertThat(SettingsViewModel.isValidIpv4(it)).isFalse()
        }
    }
}
