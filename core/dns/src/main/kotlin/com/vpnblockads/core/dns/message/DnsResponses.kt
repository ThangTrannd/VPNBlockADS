package com.vpnblockads.core.dns.message

import com.vpnblockads.core.dns.packet.putU16
import com.vpnblockads.core.dns.packet.putU32
import com.vpnblockads.core.model.BlockResponseMode

/** Tự tạo câu trả lời DNS (không cần hỏi upstream). */
object DnsResponses {
    /** TTL cho câu trả lời "đã chặn": ngắn để bỏ chặn có hiệu lực nhanh. */
    const val BLOCKED_TTL_SECONDS = 60L

    fun blocked(query: DnsQuery, mode: BlockResponseMode): ByteArray = when (mode) {
        BlockResponseMode.NXDOMAIN -> error(query, DnsMessage.RCODE_NXDOMAIN)
        BlockResponseMode.REFUSED -> error(query, DnsMessage.RCODE_REFUSED)
        BlockResponseMode.NULL_IP -> nullAddress(query)
    }

    fun nxDomain(query: DnsQuery): ByteArray = error(query, DnsMessage.RCODE_NXDOMAIN)

    fun servFail(query: DnsQuery): ByteArray = error(query, DnsMessage.RCODE_SERVFAIL)

    /** Header + nguyên question của truy vấn, không có answer. */
    fun error(query: DnsQuery, rcode: Int): ByteArray {
        val out = ByteArray(query.questionEnd)
        query.raw.copyInto(out, 0, 0, query.questionEnd)
        writeHeader(out, query, rcode, answerCount = 0)
        return out
    }

    /**
     * A -> 0.0.0.0, AAAA -> ::, loại khác (HTTPS, MX...) -> NOERROR không có answer (NODATA).
     */
    fun nullAddress(query: DnsQuery): ByteArray {
        val rdLength = when {
            query.question.dnsClass != DnsMessage.CLASS_IN -> 0
            query.question.type == DnsMessage.TYPE_A -> 4
            query.question.type == DnsMessage.TYPE_AAAA -> 16
            else -> 0
        }
        if (rdLength == 0) return error(query, DnsMessage.RCODE_NOERROR)

        val answerSize = 2 + 2 + 2 + 4 + 2 + rdLength
        val out = ByteArray(query.questionEnd + answerSize)
        query.raw.copyInto(out, 0, 0, query.questionEnd)
        writeHeader(out, query, DnsMessage.RCODE_NOERROR, answerCount = 1)
        var pos = query.questionEnd
        out.putU16(pos, 0xC000 or DnsMessage.HEADER_SIZE) // con trỏ nén tới QNAME ở offset 12
        out.putU16(pos + 2, query.question.type)
        out.putU16(pos + 4, DnsMessage.CLASS_IN)
        out.putU32(pos + 6, BLOCKED_TTL_SECONDS)
        out.putU16(pos + 10, rdLength)
        pos += 12 // rdata = toàn số 0 (mảng mới đã là 0)
        check(pos + rdLength == out.size)
        return out
    }

    private fun writeHeader(out: ByteArray, query: DnsQuery, rcode: Int, answerCount: Int) {
        val flags = DnsMessage.FLAG_QR or
            (query.flags and DnsMessage.OPCODE_MASK) or
            (query.flags and DnsMessage.FLAG_RD) or
            DnsMessage.FLAG_RA or
            (rcode and DnsMessage.RCODE_MASK)
        out.putU16(0, query.id)
        out.putU16(2, flags)
        out.putU16(4, 1)
        out.putU16(6, answerCount)
        out.putU16(8, 0)
        out.putU16(10, 0) // bỏ phần additional (vd bản ghi EDNS OPT của truy vấn)
    }
}
