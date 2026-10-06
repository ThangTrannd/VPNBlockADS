package com.vpnblockads.core.domain.repository

import com.vpnblockads.core.model.AppSettings
import com.vpnblockads.core.model.BlockResponseMode
import com.vpnblockads.core.model.BlocklistStatus
import com.vpnblockads.core.model.CustomRule
import com.vpnblockads.core.model.QueryLogEntry
import com.vpnblockads.core.model.RuleKind
import com.vpnblockads.core.model.UpstreamDns
import com.vpnblockads.core.model.VpnStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setUpstream(upstream: UpstreamDns)
    suspend fun setBlockMode(mode: BlockResponseMode)
    suspend fun setBlocklistUrl(url: String)
}

interface BlocklistRepository {
    /** Tập domain hiện tại (rỗng cho tới khi load xong). */
    val domains: StateFlow<Set<String>>
    val status: StateFlow<BlocklistStatus>

    /** Load từ file đã tải (nếu có) hoặc từ assets. Gọi nhiều lần chỉ load một lần. */
    suspend fun ensureLoaded()

    /** Tải danh sách từ URL, thay thế danh sách hiện tại. Trả về số domain. */
    suspend fun updateFromUrl(url: String): Result<Int>
}

interface CustomRuleRepository {
    val rules: Flow<List<CustomRule>>
    suspend fun upsert(domain: String, kind: RuleKind)
    suspend fun remove(domain: String)
}

interface QueryLogRepository {
    /** Ghi log không chặn luồng gọi (được gom lô ghi xuống DB). */
    fun record(entry: QueryLogEntry)
    fun recent(limit: Int): Flow<List<QueryLogEntry>>
    fun blockedCountSince(sinceMillis: Long): Flow<Int>
    fun totalCountSince(sinceMillis: Long): Flow<Int>
    suspend fun deleteOlderThan(cutoffMillis: Long)
    suspend fun clear()
}

/** Điều khiển VPN từ UI. Quyền VPN (VpnService.prepare) do UI xin trước khi gọi [start]. */
interface VpnController {
    val status: StateFlow<VpnStatus>
    fun start()
    fun stop()
}
