package com.vpnblockads.core.vpn

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.Closeable
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bọc file descriptor của TUN.
 *
 * TUN là card mạng ảo: mỗi lần `read()` nhận đúng **một gói IP** mà một app gửi
 * vào route của VPN; mỗi lần `write()` một gói IP thì kernel coi như gói đó vừa
 * "đến" từ mạng và giao cho app đích.
 *
 * Vấn đề: `read()` chặn (block) và đóng fd từ luồng khác không đảm bảo đánh thức nó.
 * Giải pháp: dùng `poll()` chờ đồng thời trên fd TUN và một **pipe**; muốn dừng thì
 * đóng đầu ghi của pipe -> poll trả về ngay (POLLHUP) -> vòng đọc thoát sạch.
 */
internal class TunDevice(private val pfd: ParcelFileDescriptor) : Closeable {
    private val input = FileInputStream(pfd.fileDescriptor)
    private val output = FileOutputStream(pfd.fileDescriptor)
    private val wakeRead: FileDescriptor
    private val wakeWrite: FileDescriptor
    private val stopRequested = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    init {
        val pipe = Os.pipe()
        wakeRead = pipe[0]
        wakeWrite = pipe[1]
    }

    /**
     * Chặn luồng hiện tại, gọi [onPacket] cho từng gói đọc được. [buffer] được tái
     * sử dụng giữa các lần gọi nên [onPacket] phải chép dữ liệu nếu cần giữ lại.
     * Thoát khi [requestStop] được gọi hoặc TUN bị đóng.
     */
    fun readLoop(onPacket: (buffer: ByteArray, length: Int) -> Unit) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        val wakePoll = StructPollfd().apply {
            fd = wakeRead
            events = (OsConstants.POLLHUP or OsConstants.POLLERR or OsConstants.POLLIN).toShort()
        }
        val tunPoll = StructPollfd().apply {
            fd = pfd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val polls = arrayOf(wakePoll, tunPoll)

        while (!stopRequested.get()) {
            wakePoll.revents = 0
            tunPoll.revents = 0
            try {
                Os.poll(polls, -1)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) continue
                throw IOException("poll failed", e)
            }
            if (wakePoll.revents.toInt() != 0) return
            val revents = tunPoll.revents.toInt()
            if (revents and OsConstants.POLLIN != 0) {
                val length = input.read(buffer)
                if (length < 0) return
                if (length > 0) onPacket(buffer, length)
            }
            if (revents and (OsConstants.POLLHUP or OsConstants.POLLERR or OsConstants.POLLNVAL) != 0) {
                throw IOException("tun closed (revents=$revents)")
            }
        }
    }

    /** Ghi một gói IP hoàn chỉnh. Chỉ gọi từ MỘT coroutine/luồng (single writer). */
    fun write(packet: ByteArray) {
        output.write(packet)
    }

    /** true nếu vòng đọc thoát vì ta chủ động dừng (không phải vì lỗi). */
    val isStopRequested: Boolean get() = stopRequested.get()

    /** Đánh thức [readLoop] để nó thoát. An toàn khi gọi nhiều lần, từ luồng bất kỳ. */
    fun requestStop() {
        if (stopRequested.compareAndSet(false, true)) closeQuietly(wakeWrite)
    }

    override fun close() {
        requestStop()
        if (closed.compareAndSet(false, true)) {
            closeQuietly(wakeRead)
            try {
                pfd.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun closeQuietly(fd: FileDescriptor) {
        try {
            Os.close(fd)
        } catch (_: ErrnoException) {
        }
    }

    private companion object {
        const val MAX_PACKET_SIZE = 32_767
    }
}
