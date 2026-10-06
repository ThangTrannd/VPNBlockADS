package com.vpnblockads.core.dns

import com.vpnblockads.core.dns.cache.DnsCache
import com.vpnblockads.core.dns.filter.DomainFilter
import com.vpnblockads.core.dns.filter.FilterDecision
import com.vpnblockads.core.dns.message.DnsMessage
import com.vpnblockads.core.dns.message.DnsResponses
import com.vpnblockads.core.dns.packet.DnsQueryPacket
import com.vpnblockads.core.dns.packet.PacketBuilder
import com.vpnblockads.core.dns.upstream.DnsUpstream
import com.vpnblockads.core.model.BlockResponseMode
import com.vpnblockads.core.model.QueryLogEntry
import com.vpnblockads.core.model.QueryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Toàn bộ quy trình cho một truy vấn: parse -> lọc -> cache -> upstream -> đóng gói.
 * Không phụ thuộc Android: service chỉ việc đưa gói vào và ghi kết quả ra TUN.
 */
class DnsRequestHandler(
    private val filterProvider: () -> DomainFilter,
    private val blockModeProvider: () -> BlockResponseMode,
    private val upstream: DnsUpstream,
    private val cache: DnsCache? = null,
    private val upstreamTimeoutMillis: Long = 5_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onQuery: (QueryLogEntry) -> Unit = {},
) {

    /** Trả về gói IP hoàn chỉnh để ghi vào TUN, hoặc null nếu bỏ qua. */
    suspend fun handle(packet: DnsQueryPacket): ByteArray? {
        val response = resolve(packet.dnsPayload) ?: return null
        return PacketBuilder.buildResponse(packet, response)
    }

    /** Trả về DNS response thô, hoặc null nếu truy vấn không hợp lệ (drop). */
    suspend fun resolve(payload: ByteArray): ByteArray? {
        val start = clock()
        val query = DnsMessage.parseQuery(payload) ?: return null
        val question = query.question

        fun log(status: QueryStatus) = onQuery(
            QueryLogEntry(
                domain = question.name,
                type = question.type,
                timestampMillis = start,
                status = status,
                latencyMillis = clock() - start,
            ),
        )

        val decision = filterProvider().decide(question.name)
        if (decision == FilterDecision.BLOCK) {
            return DnsResponses.blocked(query, blockModeProvider()).also { log(QueryStatus.BLOCKED) }
        }
        val allowedStatus =
            if (decision == FilterDecision.ALLOW_WHITELISTED) QueryStatus.WHITELISTED else QueryStatus.ALLOWED

        cache?.get(query)?.let { cached ->
            return cached.also { log(if (decision == FilterDecision.ALLOW) QueryStatus.CACHED else allowedStatus) }
        }

        return try {
            val response = withTimeout(upstreamTimeoutMillis) { upstream.resolve(payload) }
            cache?.put(query, response)
            response.also { log(allowedStatus) }
        } catch (e: TimeoutCancellationException) {
            DnsResponses.servFail(query).also { log(QueryStatus.FAILED) }
        } catch (e: CancellationException) {
            throw e // scope bị huỷ (service dừng) -> để coroutine kết thúc bình thường
        } catch (e: Exception) {
            // Fail-safe: mọi lỗi upstream đều trả SERVFAIL để app không phải chờ timeout.
            DnsResponses.servFail(query).also { log(QueryStatus.FAILED) }
        }
    }
}
