package com.vpnblockads.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnblockads.core.domain.repository.BlocklistRepository
import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.domain.repository.SettingsRepository
import com.vpnblockads.core.domain.usecase.AddCustomRuleUseCase
import com.vpnblockads.core.model.AppSettings
import com.vpnblockads.core.model.BlockResponseMode
import com.vpnblockads.core.model.BlocklistStatus
import com.vpnblockads.core.model.CustomRule
import com.vpnblockads.core.model.RuleKind
import com.vpnblockads.core.model.UpstreamDns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val blocklist: BlocklistStatus = BlocklistStatus(),
    val rules: List<CustomRule> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val blocklistRepository: BlocklistRepository,
    private val customRuleRepository: CustomRuleRepository,
    private val addCustomRule: AddCustomRuleUseCase,
) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        blocklistRepository.status,
        customRuleRepository.rules,
        ::SettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { blocklistRepository.ensureLoaded() }
    }

    fun selectUpstream(upstream: UpstreamDns) = viewModelScope.launch {
        settingsRepository.setUpstream(upstream)
    }

    /** Chỉ nhận địa chỉ IPv4 dạng số (không nhận hostname: phân giải hostname lại cần DNS). */
    fun setCustomUpstream(raw: String) = viewModelScope.launch {
        val address = raw.trim()
        if (isValidIpv4(address)) {
            settingsRepository.setUpstream(UpstreamDns.Custom(address))
            _messages.send("Đã dùng DNS $address")
        } else {
            _messages.send("Địa chỉ IPv4 không hợp lệ")
        }
    }

    fun setBlockMode(mode: BlockResponseMode) = viewModelScope.launch { settingsRepository.setBlockMode(mode) }

    fun updateBlocklist(url: String) = viewModelScope.launch {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            _messages.send("URL phải bắt đầu bằng http:// hoặc https://")
            return@launch
        }
        settingsRepository.setBlocklistUrl(trimmed)
        blocklistRepository.updateFromUrl(trimmed)
            .onSuccess { _messages.send("Đã cập nhật: %,d domain".format(it)) }
            .onFailure { _messages.send("Cập nhật thất bại: ${it.message}") }
    }

    fun addRule(domain: String, kind: RuleKind) = viewModelScope.launch {
        if (addCustomRule(domain, kind) == AddCustomRuleUseCase.Result.InvalidDomain) {
            _messages.send("Domain không hợp lệ")
        }
    }

    fun removeRule(domain: String) = viewModelScope.launch { customRuleRepository.remove(domain) }

    companion object {
        private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")

        fun isValidIpv4(value: String) = IPV4.matches(value)
    }
}
