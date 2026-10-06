package com.vpnblockads.core.data.repository

import com.vpnblockads.core.data.database.QueryLogDao
import com.vpnblockads.core.data.database.toEntity
import com.vpnblockads.core.data.database.toModel
import com.vpnblockads.core.data.di.ApplicationScope
import com.vpnblockads.core.domain.repository.QueryLogRepository
import com.vpnblockads.core.model.QueryLogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mỗi truy vấn DNS sinh 1 log; insert từng dòng sẽ tốn pin và làm Flow của UI
 * phát lại liên tục. Vì vậy log được đẩy vào Channel rồi ghi theo lô mỗi giây.
 */
@Singleton
class QueryLogRepositoryImpl @Inject constructor(
    private val dao: QueryLogDao,
    @ApplicationScope scope: CoroutineScope,
) : QueryLogRepository {

    private val buffer = Channel<QueryLogEntry>(
        capacity = 5_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                val batch = mutableListOf(buffer.receive())
                delay(FLUSH_INTERVAL_MILLIS)
                while (true) batch += buffer.tryReceive().getOrNull() ?: break
                try {
                    dao.insertAll(batch.map { it.toEntity() })
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Log chỉ để xem: lỗi DB không được làm ảnh hưởng việc lọc DNS.
                }
            }
        }
    }

    override fun record(entry: QueryLogEntry) {
        buffer.trySend(entry)
    }

    override fun recent(limit: Int): Flow<List<QueryLogEntry>> =
        dao.recent(limit).map { list -> list.map { it.toModel() } }

    override fun blockedCountSince(sinceMillis: Long): Flow<Int> = dao.blockedCountSince(sinceMillis)

    override fun totalCountSince(sinceMillis: Long): Flow<Int> = dao.totalCountSince(sinceMillis)

    override suspend fun deleteOlderThan(cutoffMillis: Long) = dao.deleteOlderThan(cutoffMillis)

    override suspend fun clear() = dao.clear()

    private companion object {
        const val FLUSH_INTERVAL_MILLIS = 1_000L
    }
}
