package com.vpnblockads.core.data.repository

import android.content.Context
import android.util.Log
import com.vpnblockads.core.dns.filter.HostsParser
import com.vpnblockads.core.domain.repository.BlocklistRepository
import com.vpnblockads.core.model.BlocklistStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocklist ~70-150k domain giữ trong HashSet (vài chục MB RAM tối đa, tra cứu O(1)).
 * Bản tải về lưu ở filesDir; chưa tải thì dùng bản đóng gói trong assets.
 * Không lưu vào Room: chỉ cần đọc tuần tự một lần lúc khởi động.
 */
@Singleton
class BlocklistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BlocklistRepository {

    private val downloadedFile = File(context.filesDir, "blocklist/hosts.txt")
    private val mutex = Mutex()
    private var loaded = false

    private val _domains = MutableStateFlow<Set<String>>(emptySet())
    override val domains: StateFlow<Set<String>> = _domains.asStateFlow()

    private val _status = MutableStateFlow(BlocklistStatus())
    override val status: StateFlow<BlocklistStatus> = _status.asStateFlow()

    override suspend fun ensureLoaded() = mutex.withLock {
        if (loaded) return@withLock
        _status.update { it.copy(isLoading = true) }
        val startNanos = System.nanoTime()
        val set = withContext(Dispatchers.IO) {
            try {
                if (downloadedFile.exists()) {
                    downloadedFile.bufferedReader().use { HostsParser.parse(it) }
                } else {
                    context.assets.open(ASSET_NAME).bufferedReader().use { HostsParser.parse(it) }
                }
            } catch (e: IOException) {
                emptySet()
            }
        }
        Log.i(TAG, "loaded ${set.size} domains in ${(System.nanoTime() - startNanos) / 1_000_000}ms")
        publish(set, downloadedFile.takeIf { it.exists() }?.lastModified())
        loaded = true
    }

    override suspend fun updateFromUrl(url: String): Result<Int> = mutex.withLock {
        _status.update { it.copy(isLoading = true) }
        try {
            val set = withContext(Dispatchers.IO) {
                downloadedFile.parentFile?.mkdirs()
                val temp = File(downloadedFile.parentFile, "hosts.download")
                download(url, temp)
                val parsed = temp.bufferedReader().use { HostsParser.parse(it) }
                if (parsed.isEmpty()) {
                    temp.delete()
                    throw IOException("Không tìm thấy domain hợp lệ trong file tải về")
                }
                // Đổi tên trong cùng thư mục là thao tác nguyên tử: không bao giờ có file dở dang.
                if (!temp.renameTo(downloadedFile)) throw IOException("Không lưu được file")
                parsed
            }
            publish(set, downloadedFile.lastModified())
            loaded = true
            Result.success(set.size)
        } catch (e: CancellationException) {
            _status.update { it.copy(isLoading = false) }
            throw e
        } catch (e: Exception) {
            _status.update { it.copy(isLoading = false) }
            Result.failure(e)
        }
    }

    private fun publish(set: Set<String>, lastUpdated: Long?) {
        _domains.value = set
        _status.value = BlocklistStatus(domainCount = set.size, isLoading = false, lastUpdatedMillis = lastUpdated)
    }

    private fun download(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ASSET_NAME = "hosts.txt"
        const val TAG = "Blocklist"
    }
}
