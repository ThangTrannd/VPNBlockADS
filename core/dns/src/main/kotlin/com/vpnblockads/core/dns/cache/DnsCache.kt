package com.vpnblockads.core.dns.cache

import com.vpnblockads.core.dns.message.DnsFormatException
import com.vpnblockads.core.dns.message.DnsMessage
import com.vpnblockads.core.dns.message.DnsQuery
import com.vpnblockads.core.dns.message.DnsQuestion
import com.vpnblockads.core.dns.packet.putU16
import com.vpnblockads.core.dns.packet.putU32
import com.vpnblockads.core.dns.packet.u32

/**
 * Cache câu trả lời DNS theo TTL, giới hạn số mục theo kiểu LRU.
 *
 * Chi tiết dễ sai:
 * - Key = (tên, type, class): "A example.com" và "AAAA example.com" là hai mục khác nhau.
 * - Khi trả từ cache phải ghi lại **ID** của truy vấn mới, nếu không client sẽ bỏ câu trả lời.
 * - TTL trong câu trả lời phải **giảm** theo thời gian đã nằm trong cache.
 * - Không cache SERVFAIL hay câu trả lời bị cắt (cờ TC).
 * - NXDOMAIN/NODATA cache theo SOA trong authority (RFC 2308).
 */
class DnsCache(
    private val maxEntries: Int = 2_000,
    private val maxTtlSeconds: Long = 3_600,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(
        val response: ByteArray,
        val ttlRecords: List<Pair<Int, Long>>, // (offset của TTL, TTL gốc)
        val questionEnd: Int,
        val storedAtMillis: Long,
        val expiresAtMillis: Long,
    )

    private val entries = object : LinkedHashMap<DnsQuestion, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DnsQuestion, Entry>) = size > maxEntries
    }

    val size: Int get() = synchronized(entries) { entries.size }

    fun clear() = synchronized(entries) { entries.clear() }

    fun get(query: DnsQuery): ByteArray? {
        val now = clock()
        val entry = synchronized(entries) {
            val e = entries[query.question] ?: return null
            if (now >= e.expiresAtMillis) {
                entries.remove(query.question)
                return null
            }
            e
        }
        val elapsedSeconds = (now - entry.storedAtMillis) / 1000
        val out = entry.response.copyOf()
        out.putU16(0, query.id)
        // Chép lại question của truy vấn MỚI: key so sánh không phân biệt hoa/thường nhưng
        // client có thể kiểm tra câu trả lời khớp đúng từng byte ("GitHub.com" vs "github.com").
        if (entry.questionEnd == query.questionEnd) {
            query.raw.copyInto(out, DnsMessage.HEADER_SIZE, DnsMessage.HEADER_SIZE, query.questionEnd)
        }
        for ((offset, ttl) in entry.ttlRecords) out.putU32(offset, (ttl - elapsedSeconds).coerceAtLeast(0))
        return out
    }

    fun put(query: DnsQuery, response: ByteArray) {
        val ttl = cacheableTtl(response) ?: return
        val now = clock()
        val ttlRecords = DnsMessage.records(response)
            .filter { it.type != DnsMessage.TYPE_OPT }
            .map { it.ttlOffset to it.ttl }
        synchronized(entries) {
            entries[query.question] = Entry(response.copyOf(), ttlRecords, questionEnd(response), now, now + ttl * 1000)
        }
    }

    private fun questionEnd(response: ByteArray): Int =
        DnsMessage.skipName(response, DnsMessage.HEADER_SIZE) + 4

    /** TTL (giây) nên cache, hoặc null nếu không nên cache. */
    internal fun cacheableTtl(response: ByteArray): Long? = try {
        if (response.size < DnsMessage.HEADER_SIZE ||
            DnsMessage.flags(response) and DnsMessage.FLAG_TC != 0
        ) {
            null
        } else {
            val records = DnsMessage.records(response).filter { it.type != DnsMessage.TYPE_OPT }
            val answers = records.filter { it.section == DnsMessage.Section.ANSWER }
            val ttl = when (DnsMessage.rcode(response)) {
                DnsMessage.RCODE_NOERROR ->
                    if (answers.isNotEmpty()) answers.minOf { it.ttl } else negativeTtl(response, records)
                DnsMessage.RCODE_NXDOMAIN -> negativeTtl(response, records)
                else -> null
            }
            ttl?.coerceAtMost(maxTtlSeconds)?.takeIf { it > 0 }
        }
    } catch (_: DnsFormatException) {
        null
    }

    /** TTL phủ định = min(TTL của SOA, trường MINIMUM — 4 byte cuối RDATA của SOA). */
    private fun negativeTtl(
        response: ByteArray,
        records: List<com.vpnblockads.core.dns.message.ResourceRecordRef>,
    ): Long? {
        val soa = records.firstOrNull {
            it.section == DnsMessage.Section.AUTHORITY && it.type == DnsMessage.TYPE_SOA && it.rdLength >= 20
        } ?: return null
        val minimum = response.u32(soa.rdataOffset + soa.rdLength - 4)
        return minOf(soa.ttl, minimum)
    }
}
