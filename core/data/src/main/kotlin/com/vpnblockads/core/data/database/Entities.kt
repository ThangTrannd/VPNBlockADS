package com.vpnblockads.core.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vpnblockads.core.model.CustomRule
import com.vpnblockads.core.model.QueryLogEntry
import com.vpnblockads.core.model.QueryStatus
import com.vpnblockads.core.model.RuleKind

@Entity(tableName = "query_log", indices = [Index("timestamp")])
data class QueryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val type: Int,
    val timestamp: Long,
    val status: String,
    val latencyMillis: Long,
)

@Entity(tableName = "custom_rule")
data class CustomRuleEntity(
    @PrimaryKey val domain: String,
    val kind: String,
    val addedAt: Long,
)

internal fun QueryLogEntry.toEntity() = QueryLogEntity(
    domain = domain,
    type = type,
    timestamp = timestampMillis,
    status = status.name,
    latencyMillis = latencyMillis,
)

internal fun QueryLogEntity.toModel() = QueryLogEntry(
    id = id,
    domain = domain,
    type = type,
    timestampMillis = timestamp,
    status = runCatching { QueryStatus.valueOf(status) }.getOrDefault(QueryStatus.ALLOWED),
    latencyMillis = latencyMillis,
)

internal fun CustomRuleEntity.toModel() = CustomRule(
    domain = domain,
    kind = runCatching { RuleKind.valueOf(kind) }.getOrDefault(RuleKind.ALLOW),
    addedAtMillis = addedAt,
)
