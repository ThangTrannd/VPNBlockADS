package com.vpnblockads.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.domain.repository.QueryLogRepository
import com.vpnblockads.core.domain.usecase.AddCustomRuleUseCase
import com.vpnblockads.core.model.QueryLogEntry
import com.vpnblockads.core.model.RuleKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogFilter { ALL, BLOCKED, ALLOWED }

data class LogItem(val entry: QueryLogEntry, val rule: RuleKind?)

data class LogsUiState(
    val filter: LogFilter = LogFilter.ALL,
    val items: List<LogItem> = emptyList(),
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val queryLogRepository: QueryLogRepository,
    private val customRuleRepository: CustomRuleRepository,
    private val addCustomRule: AddCustomRuleUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<LogsUiState> = combine(
        queryLogRepository.recent(MAX_ITEMS),
        customRuleRepository.rules,
        filter,
    ) { logs, rules, currentFilter ->
        val ruleByDomain = rules.associate { it.domain to it.kind }
        LogsUiState(
            filter = currentFilter,
            items = logs
                .filter {
                    when (currentFilter) {
                        LogFilter.ALL -> true
                        LogFilter.BLOCKED -> it.status.isBlocked
                        LogFilter.ALLOWED -> !it.status.isBlocked
                    }
                }
                .map { LogItem(it, ruleByDomain[it.domain]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogsUiState())

    fun setFilter(value: LogFilter) {
        filter.value = value
    }

    fun addRule(domain: String, kind: RuleKind) = viewModelScope.launch {
        val message = when (val result = addCustomRule(domain, kind)) {
            is AddCustomRuleUseCase.Result.Added ->
                if (kind == RuleKind.ALLOW) "Đã thêm ${result.domain} vào whitelist" else "Đã chặn ${result.domain}"
            AddCustomRuleUseCase.Result.InvalidDomain -> "Domain không hợp lệ"
        }
        _messages.send(message)
    }

    fun removeRule(domain: String) = viewModelScope.launch {
        customRuleRepository.remove(domain)
        _messages.send("Đã xoá quy tắc cho $domain")
    }

    fun clearLogs() = viewModelScope.launch { queryLogRepository.clear() }

    private companion object {
        const val MAX_ITEMS = 500
    }
}
