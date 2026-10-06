package com.vpnblockads.core.dns.upstream

import com.vpnblockads.core.dns.message.DnsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Nơi thực sự trả lời truy vấn. Interface để sau này thêm DoH (DNS-over-HTTPS)
 * hoặc TCP mà không phải sửa phần xử lý gói.
 */
interface DnsUpstream {
    /** Gửi DNS message thô, nhận DNS message thô. Lỗi/timeout -> throw IOException. */
    suspend fun resolve(query: ByteArray): ByteArray
}

/**
 * Trừu tượng hoá VpnService.protect(): socket được "protect" sẽ đi thẳng ra mạng
 * thật, không bị route ngược vào TUN của chính mình (tránh vòng lặp vô hạn).
 */
fun interface SocketProtector {
    fun protect(socket: DatagramSocket): Boolean
}

/**
 * Forward qua UDP. Mỗi truy vấn dùng một socket mới: đơn giản, an toàn khi
 * máy đổi mạng (Wi-Fi <-> 4G), chi phí không đáng kể so với độ trễ mạng.
 */
class UdpDnsUpstream(
    private val serverProvider: () -> InetSocketAddress,
    private val protector: SocketProtector,
    private val timeoutMillis: Long = 4_000,
    /** UDP có thể mất gói: chưa có trả lời sau khoảng này thì gửi lại (như resolver thật). */
    private val retransmitMillis: Long = 1_200,
) : DnsUpstream {

    override suspend fun resolve(query: ByteArray): ByteArray = runInterruptible(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            if (!protector.protect(socket)) throw IOException("protect() failed")
            // connect() để kernel tự lọc bỏ gói đến từ địa chỉ khác.
            socket.connect(serverProvider())

            val expectedId = DnsMessage.id(query)
            val buffer = ByteArray(MAX_RESPONSE_SIZE)
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            while (true) {
                val remaining = (deadline - System.nanoTime()) / 1_000_000
                if (remaining <= 0) throw SocketTimeoutException("no response in ${timeoutMillis}ms")
                socket.send(DatagramPacket(query, query.size))
                // Chờ tới lần gửi lại tiếp theo (hoặc tới hạn chót, tuỳ cái nào sớm hơn).
                val attemptDeadline = System.nanoTime() + minOf(retransmitMillis, remaining) * 1_000_000
                try {
                    while (true) {
                        val wait = (attemptDeadline - System.nanoTime()) / 1_000_000
                        if (wait <= 0) break
                        socket.soTimeout = wait.toInt()
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        if (packet.length >= DnsMessage.HEADER_SIZE && DnsMessage.id(buffer) == expectedId) {
                            return@runInterruptible buffer.copyOf(packet.length)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // hết lượt chờ -> vòng ngoài gửi lại
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IOException("unreachable")
        }
    }

    private companion object {
        const val MAX_RESPONSE_SIZE = 4096
    }
}
