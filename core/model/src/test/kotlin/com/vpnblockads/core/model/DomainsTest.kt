package com.vpnblockads.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainsTest {
    @Test
    fun `valid domains`() {
        listOf("a.com", "ads.example.co.uk", "x-y.z", "_dmarc.example.com", "123.example", "a".repeat(63) + ".com")
            .forEach { assertThat(Domains.isValid(it)).isTrue() }
    }

    @Test
    fun `invalid domains`() {
        listOf("", ".", "a..com", ".a.com", "-a.com", "a-.com", "a b.com", "ads!.com", "UPPER.com", "a".repeat(64) + ".com")
            .forEach { assertThat(Domains.isValid(it)).isFalse() }
    }

    @Test
    fun `normalize lowercases trims and drops trailing dot`() {
        assertThat(Domains.normalize("  Ads.Example.COM. ")).isEqualTo("ads.example.com")
    }
}
