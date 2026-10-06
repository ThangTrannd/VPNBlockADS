package com.vpnblockads.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryLogDao {
    @Insert
    suspend fun insertAll(entries: List<QueryLogEntity>)

    @Query("SELECT * FROM query_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<QueryLogEntity>>

    @Query("SELECT COUNT(*) FROM query_log WHERE status = 'BLOCKED' AND timestamp >= :since")
    fun blockedCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM query_log WHERE timestamp >= :since")
    fun totalCountSince(since: Long): Flow<Int>

    @Query("DELETE FROM query_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM query_log")
    suspend fun clear()
}

@Dao
interface CustomRuleDao {
    @Query("SELECT * FROM custom_rule ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Upsert
    suspend fun upsert(rule: CustomRuleEntity)

    @Query("DELETE FROM custom_rule WHERE domain = :domain")
    suspend fun delete(domain: String)
}
