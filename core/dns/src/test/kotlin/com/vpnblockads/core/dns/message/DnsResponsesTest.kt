package com.vpnblockads.core.dns.message

import com.google.common.truth.Truth.assertThat
import com.vpnblockads.core.dns.TestDns
import com.vpnblockads.core.model.BlockResponseMode
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import org.xbill.DNS.Type

class DnsResponsesTest {

    @Test
    fun `parseQuery reads id name type`() {
        val query = TestDns.query("Ads.Example.COM", Type.HTTPS, id = 0xBEEF)
        assertThat(query.id).isEqualTo(0xBEEF)
        assertThat(query.question.name).isEqualTo("ads.example.com")
        assertThat(query.question.type).isEqualTo(Type.HTTPS)
    }

    @Test
    fun `parseQuery rejects responses and garbage`() {
        assertThat(DnsMessage.parseQuery(TestDns.aResponse("a.com"))).isNull()
        assertThat(DnsMessage.parseQuery(ByteArray(5))).isNull()
        assertThat(DnsMessage.parseQuery(ByteArray(40) { 0x3F })).isNull()
    }

    @Test
    fun `nxdomain keeps id and question and sets flags`() {
        val query = TestDns.query("ads.example.com", id = 0x4242)
        val response = Message(DnsResponses.nxDomain(query))

        assertThat(response.header.id).isEqualTo(0x4242)
        assertThat(response.rcode).isEqualTo(Rcode.NXDOMAIN)
        assertThat(response.header.getFlag(Flags.QR.toInt())).isTrue()
        assertThat(response.header.getFlag(Flags.RD.toInt())).isTrue() // copy từ truy vấn
        assertThat(response.header.getFlag(Flags.RA.toInt())).isTrue()
        assertThat(response.question.name.toString()).isEqualTo("ads.example.com.")
        assertThat(response.question.type).isEqualTo(Type.A)
        assertThat(response.getSection(Section.ANSWER)).isEmpty()
    }

    @Test
    fun `nxdomain works for https query type`() {
        val response = Message(DnsResponses.blocked(TestDns.query("ads.com", Type.HTTPS), BlockResponseMode.NXDOMAIN))
        assertThat(response.rcode).isEqualTo(Rcode.NXDOMAIN)
        assertThat(response.question.type).isEqualTo(Type.HTTPS)
    }

    @Test
    fun `null ip mode answers zero addresses`() {
        val a = Message(DnsResponses.blocked(TestDns.query("ads.com", Type.A), BlockResponseMode.NULL_IP))
        val aaaa = Message(DnsResponses.blocked(TestDns.query("ads.com", Type.AAAA), BlockResponseMode.NULL_IP))
        val https = Message(DnsResponses.blocked(TestDns.query("ads.com", Type.HTTPS), BlockResponseMode.NULL_IP))

        assertThat(a.rcode).isEqualTo(Rcode.NOERROR)
        assertThat((a.getSection(Section.ANSWER).single() as ARecord).address.hostAddress).isEqualTo("0.0.0.0")
        assertThat((aaaa.getSection(Section.ANSWER).single() as AAAARecord).address.hostAddress).isEqualTo("0:0:0:0:0:0:0:0")
        assertThat(https.rcode).isEqualTo(Rcode.NOERROR)
        assertThat(https.getSection(Section.ANSWER)).isEmpty()
    }

    @Test
    fun `refused and servfail rcodes`() {
        val query = TestDns.query("ads.com")
        assertThat(Message(DnsResponses.blocked(query, BlockResponseMode.REFUSED)).rcode).isEqualTo(Rcode.REFUSED)
        assertThat(Message(DnsResponses.servFail(query)).rcode).isEqualTo(Rcode.SERVFAIL)
    }

    @Test
    fun `records lists ttl offsets`() {
        val response = TestDns.aResponse("example.com", ttl = 1234)
        val record = DnsMessage.records(response).single()
        assertThat(record.type).isEqualTo(Type.A)
        assertThat(record.ttl).isEqualTo(1234)
        assertThat(record.section).isEqualTo(DnsMessage.Section.ANSWER)
    }
}
