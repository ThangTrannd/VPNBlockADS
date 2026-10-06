package com.vpnblockads.core.dns.message

import com.vpnblockads.core.dns.packet.u16
import com.vpnblockads.core.dns.packet.u32
import com.vpnblockads.core.dns.packet.u8

/**
 * Wire format của DNS message (RFC 1035):
 * ```
 * Header 12 byte: ID | FLAGS | QDCOUNT | ANCOUNT | NSCOUNT | ARCOUNT  (mỗi trường 16 bit)
 *   FLAGS: QR(1) OPCODE(4) AA(1) TC(1) RD(1) RA(1) Z(3) RCODE(4)
 * Question:  QNAME (chuỗi label: [len]bytes...[0]) | QTYPE | QCLASS
 * Answer/Authority/Additional: NAME | TYPE | CLASS | TTL(32) | RDLENGTH | RDATA
 * ```
 * NAME có thể "nén": 2 byte bắt đầu bằng 11xxxxxx là con trỏ tới offset khác trong message.
 */
object DnsMessage {
    const val HEADER_SIZE = 12

    const val FLAG_QR = 0x8000
    const val FLAG_TC = 0x0200
    const val FLAG_RD = 0x0100
    const val FLAG_RA = 0x0080
    const val OPCODE_MASK = 0x7800
    const val RCODE_MASK = 0x000F

    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_REFUSED = 5

    const val TYPE_A = 1
    const val TYPE_SOA = 6
    const val TYPE_AAAA = 28
    const val TYPE_OPT = 41
    const val CLASS_IN = 1

    private const val MAX_POINTER_JUMPS = 32

    fun id(message: ByteArray): Int = message.u16(0)
    fun flags(message: ByteArray): Int = message.u16(2)
    fun rcode(message: ByteArray): Int = flags(message) and RCODE_MASK

    /**
     * Parse một truy vấn chuẩn (QR=0, OPCODE=0, đúng 1 question).
     * Trả null nếu không phải dạng đó hoặc message hỏng.
     */
    fun parseQuery(message: ByteArray): DnsQuery? = try {
        if (message.size < HEADER_SIZE) {
            null
        } else {
            val flags = flags(message)
            val qdCount = message.u16(4)
            if (flags and FLAG_QR != 0 || flags and OPCODE_MASK != 0 || qdCount != 1) {
                null
            } else {
                val (name, afterName) = readName(message, HEADER_SIZE)
                if (afterName + 4 > message.size) {
                    null
                } else {
                    DnsQuery(
                        id = id(message),
                        flags = flags,
                        question = DnsQuestion(name, message.u16(afterName), message.u16(afterName + 2)),
                        questionEnd = afterName + 4,
                        raw = message,
                    )
                }
            }
        }
    } catch (_: DnsFormatException) {
        null
    }

    /**
     * Đọc NAME tại [offset]. Trả về (tên dạng "a.b.c" chữ thường, offset ngay sau NAME
     * ở vị trí gốc — tức không đi theo con trỏ nén).
     */
    fun readName(message: ByteArray, offset: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var pos = offset
        var endOfName = -1
        var jumps = 0
        while (true) {
            if (pos >= message.size) throw DnsFormatException("name out of bounds")
            val len = message.u8(pos)
            when {
                len == 0 -> {
                    if (endOfName < 0) endOfName = pos + 1
                    break
                }
                len and 0xC0 == 0xC0 -> {
                    if (pos + 1 >= message.size) throw DnsFormatException("bad pointer")
                    if (endOfName < 0) endOfName = pos + 2
                    if (++jumps > MAX_POINTER_JUMPS) throw DnsFormatException("pointer loop")
                    pos = ((len and 0x3F) shl 8) or message.u8(pos + 1)
                }
                len and 0xC0 != 0 -> throw DnsFormatException("unsupported label type")
                else -> {
                    if (pos + 1 + len > message.size) throw DnsFormatException("label out of bounds")
                    if (sb.isNotEmpty()) sb.append('.')
                    for (i in pos + 1..pos + len) sb.append(message[i].toInt().and(0xFF).toChar().lowercaseChar())
                    pos += len + 1
                    if (sb.length > 255) throw DnsFormatException("name too long")
                }
            }
        }
        return sb.toString() to endOfName
    }

    fun skipName(message: ByteArray, offset: Int): Int = readName(message, offset).second

    /**
     * Duyệt mọi resource record (bỏ qua phần question). Dùng cho cache:
     * cần biết TTL và vị trí trường TTL để sửa khi trả từ cache.
     */
    fun records(message: ByteArray): List<ResourceRecordRef> {
        if (message.size < HEADER_SIZE) throw DnsFormatException("short message")
        var pos = HEADER_SIZE
        repeat(message.u16(4)) { pos = skipName(message, pos) + 4 }
        val counts = intArrayOf(message.u16(6), message.u16(8), message.u16(10))
        val result = ArrayList<ResourceRecordRef>()
        Section.entries.forEachIndexed { index, section ->
            repeat(counts[index]) {
                pos = skipName(message, pos)
                if (pos + 10 > message.size) throw DnsFormatException("record out of bounds")
                val rdLength = message.u16(pos + 8)
                val rdata = pos + 10
                if (rdata + rdLength > message.size) throw DnsFormatException("rdata out of bounds")
                result += ResourceRecordRef(
                    section = section,
                    type = message.u16(pos),
                    ttlOffset = pos + 4,
                    ttl = message.u32(pos + 4),
                    rdataOffset = rdata,
                    rdLength = rdLength,
                )
                pos = rdata + rdLength
            }
        }
        return result
    }

    enum class Section { ANSWER, AUTHORITY, ADDITIONAL }
}

class DnsFormatException(message: String) : Exception(message)

data class DnsQuestion(val name: String, val type: Int, val dnsClass: Int)

class DnsQuery(
    val id: Int,
    val flags: Int,
    val question: DnsQuestion,
    /** Offset ngay sau question section — dùng để chép nguyên question vào response. */
    val questionEnd: Int,
    val raw: ByteArray,
)

class ResourceRecordRef(
    val section: DnsMessage.Section,
    val type: Int,
    val ttlOffset: Int,
    val ttl: Long,
    val rdataOffset: Int,
    val rdLength: Int,
)
