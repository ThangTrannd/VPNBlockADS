package com.vpnblockads.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnblockads.core.domain.repository.BlocklistRepository
import com.vpnblockads.core.domain.repository.QueryLogRepository
import com.vpnblockads.core.domain.repository.VpnController
import com.vpnblockads.core.model.BlocklistStatus
import com.vpnblockads.core.model.VpnStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val status: VpnStatus = VpnStatus.Stopped,
    val blockedToday: Int = 0,
    val queriesToday: Int = 0,
    val blocklist: BlocklistStatus = BlocklistStatus(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vpnController: VpnController,
    queryLogRepository: QueryLogRepository,
    private val blocklistRepository: BlocklistRepository,
) : ViewModel() {

    /** Mốc 0h hôm nay; phát lại khi qua ngày mới để bộ đếm tự reset. */
    private val startOfToday = flow {
        while (true) {
            emit(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        vpnController.status,
        startOfToday.flatMapLatest { queryLogRepository.blockedCountSince(it) },
        startOfToday.flatMapLatest { queryLogRepository.totalCountSince(it) },
        blocklistRepository.status,
    ) { status, blocked, total, blocklist ->
        HomeUiState(status, blocked, total, blocklist)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { blocklistRepository.ensureLoaded() }
    }

    fun start() = vpnController.start()

    fun stop() = vpnController.stop()
}
