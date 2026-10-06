package com.vpnblockads.core.dns.filter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainFilterTest {
    private val filter = DomainFilter(
        blocked = listOf(setOf("ads.com", "tracker.net"), setOf("custom-block.org")),
        allowed = setOf("good.ads.com", "tracker.net.vn"),
    )

    @Test
    fun `exact domain in blocklist is blocked`() {
        assertThat(filter.decide("ads.com")).isEqualTo(FilterDecision.BLOCK)
    }

    @Test
    fun `subdomain of blocked domain is blocked`() {
        assertThat(filter.decide("x.y.ads.com")).isEqualTo(FilterDecision.BLOCK)
        assertThat(filter.decide("cdn.tracker.net")).isEqualTo(FilterDecision.BLOCK)
    }

    @Test
    fun `domain sharing only a suffix string is not blocked`() {
        assertThat(filter.decide("notads.com")).isEqualTo(FilterDecision.ALLOW)
        assertThat(filter.decide("ads.com.vn")).isEqualTo(FilterDecision.ALLOW)
    }

    @Test
    fun `parent of blocked domain is not blocked`() {
        assertThat(filter.decide("com")).isEqualTo(FilterDecision.ALLOW)
        assertThat(filter.decide("net")).isEqualTo(FilterDecision.ALLOW)
    }

    @Test
    fun `every blocklist set is consulted`() {
        assertThat(filter.decide("a.custom-block.org")).isEqualTo(FilterDecision.BLOCK)
    }

    @Test
    fun `whitelist wins over blocklist including subdomains`() {
        assertThat(filter.decide("good.ads.com")).isEqualTo(FilterDecision.ALLOW_WHITELISTED)
        assertThat(filter.decide("img.good.ads.com")).isEqualTo(FilterDecision.ALLOW_WHITELISTED)
        assertThat(filter.decide("bad.ads.com")).isEqualTo(FilterDecision.BLOCK)
    }

    @Test
    fun `matching is case insensitive and ignores trailing dot`() {
        assertThat(filter.decide("WWW.Ads.COM.")).isEqualTo(FilterDecision.BLOCK)
    }

    @Test
    fun `empty filter allows everything`() {
        assertThat(DomainFilter.EMPTY.decide("ads.com")).isEqualTo(FilterDecision.ALLOW)
        assertThat(DomainFilter.EMPTY.decide("")).isEqualTo(FilterDecision.ALLOW)
    }
}
