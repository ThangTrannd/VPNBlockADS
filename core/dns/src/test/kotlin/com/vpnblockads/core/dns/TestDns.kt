package com.vpnblockads.core.dns

import com.vpnblockads.core.dns.message.DnsMessage
import com.vpnblockads.core.dns.message.DnsQuery
import com.vpnblockads.core.dns.packet.DnsQueryPacket
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.SOARecord
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

/** Tạo DNS message chuẩn bằng dnsjava để làm dữ liệu test độc lập với code đang test. */
object TestDns {
    fun queryBytes(domain: String, type: Int = Type.A, id: Int = 0x1234): ByteArray {
        val message = Message.newQuery(Record.newRecord(Name.fromString("$domain."), type, DClass.IN))
        message.header.id = id
        return message.toWire()
    }

    fun query(domain: String, type: Int = Type.A, id: Int = 0x1234): DnsQuery =
        DnsMessage.parseQuery(queryBytes(domain, type, id))!!

    fun aResponse(domain: String, ip: String = "93.184.216.34", ttl: Long = 300, id: Int = 0x1234): ByteArray {
        val query = Message(queryBytes(domain, Type.A, id))
        val response = Message(query.header.id)
        response.header.setFlag(Flags.QR.toInt())
        response.header.setFlag(Flags.RD.toInt())
        response.header.setFlag(Flags.RA.toInt())
        response.addRecord(query.question, Section.QUESTION)
        response.addRecord(
            ARecord(Name.fromString("$domain."), DClass.IN, ttl, InetAddress.getByName(ip)),
            Section.ANSWER,
        )
        return response.toWire()
    }

    fun nxDomainWithSoa(domain: String, soaTtl: Long, soaMinimum: Long, id: Int = 0x1234): ByteArray {
        val query = Message(queryBytes(domain, Type.A, id))
        val response = Message(query.header.id)
        response.header.setFlag(Flags.QR.toInt())
        response.header.rcode = Rcode.NXDOMAIN
        response.addRecord(query.question, Section.QUESTION)
        val zone = Name.fromString("example.com.")
        response.addRecord(
            SOARecord(zone, DClass.IN, soaTtl, Name.fromString("ns.example.com."),
                Name.fromString("admin.example.com."), 1, 3600, 600, 86400, soaMinimum),
            Section.AUTHORITY,
        )
        return response.toWire()
    }

    fun rcodeResponse(domain: String, rcode: Int, id: Int = 0x1234): ByteArray {
        val query = Message(queryBytes(domain, Type.A, id))
        val response = Message(query.header.id)
        response.header.setFlag(Flags.QR.toInt())
        response.header.rcode = rcode
        response.addRecord(query.question, Section.QUESTION)
        return response.toWire()
    }

    val APP_ADDRESS = byteArrayOf(10, 111, 222.toByte(), 1)
    val FAKE_DNS = byteArrayOf(10, 111, 222.toByte(), 2)

    fun packet(payload: ByteArray, sourcePort: Int = 40000) = DnsQueryPacket(
        ipVersion = 4,
        sourceAddress = APP_ADDRESS,
        destinationAddress = FAKE_DNS,
        sourcePort = sourcePort,
        destinationPort = 53,
        dnsPayload = payload,
    )
}
