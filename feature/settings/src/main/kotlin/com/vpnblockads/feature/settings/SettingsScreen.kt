package com.vpnblockads.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnblockads.core.model.BlockResponseMode
import com.vpnblockads.core.model.CustomRule
import com.vpnblockads.core.model.RuleKind
import com.vpnblockads.core.model.UpstreamDns
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            onSelectUpstream = viewModel::selectUpstream,
            onCustomUpstream = viewModel::setCustomUpstream,
            onBlockMode = viewModel::setBlockMode,
            onUpdateBlocklist = viewModel::updateBlocklist,
            onAddRule = viewModel::addRule,
            onRemoveRule = viewModel::removeRule,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSelectUpstream: (UpstreamDns) -> Unit,
    onCustomUpstream: (String) -> Unit,
    onBlockMode: (BlockResponseMode) -> Unit,
    onUpdateBlocklist: (String) -> Unit,
    onAddRule: (String, RuleKind) -> Unit,
    onRemoveRule: (String) -> Unit,
) {
    val settings = state.settings
    var customDns by rememberSaveable(settings.upstream) {
        mutableStateOf((settings.upstream as? UpstreamDns.Custom)?.address.orEmpty())
    }
    var blocklistUrl by rememberSaveable(settings.blocklistUrl) { mutableStateOf(settings.blocklistUrl) }
    var newAllow by rememberSaveable { mutableStateOf("") }
    var newBlock by rememberSaveable { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        section("DNS upstream")
        items(UpstreamDns.presets) { preset ->
            RadioRow(
                label = "${presetName(preset)} (${preset.address})",
                selected = settings.upstream == preset,
                onClick = { onSelectUpstream(preset) },
            )
        }
        item {
            RadioRow(label = "Tuỳ chỉnh", selected = settings.upstream is UpstreamDns.Custom, onClick = {
                if (SettingsViewModel.isValidIpv4(customDns)) onCustomUpstream(customDns)
            })
            InputRow(
                value = customDns,
                onValueChange = { customDns = it },
                label = "IPv4, vd 94.140.14.14",
                button = "Dùng",
                keyboardType = KeyboardType.Number,
                onSubmit = { onCustomUpstream(customDns) },
            )
        }

        section("Khi chặn, trả lời bằng")
        items(BlockResponseMode.entries) { mode ->
            RadioRow(
                label = when (mode) {
                    BlockResponseMode.NXDOMAIN -> "NXDOMAIN — domain không tồn tại (khuyên dùng)"
                    BlockResponseMode.NULL_IP -> "0.0.0.0 / :: — địa chỉ rỗng"
                    BlockResponseMode.REFUSED -> "REFUSED — từ chối"
                },
                selected = settings.blockMode == mode,
                onClick = { onBlockMode(mode) },
            )
        }

        section("Blocklist")
        item {
            val status = state.blocklist
            Text("%,d domain".format(status.domainCount), style = MaterialTheme.typography.bodyLarge)
            Text(
                status.lastUpdatedMillis?.let { "Cập nhật: " + DateFormat.getDateTimeInstance().format(Date(it)) }
                    ?: "Đang dùng bản đóng gói sẵn trong app",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = blocklistUrl,
                onValueChange = { blocklistUrl = it },
                label = { Text("URL file hosts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onUpdateBlocklist(blocklistUrl) }, enabled = !status.isLoading) {
                if (status.isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Tải & cập nhật")
                }
            }
        }

        section("Whitelist (luôn cho qua)")
        item {
            InputRow(newAllow, { newAllow = it }, "vd: ads.example.com", "Thêm") {
                onAddRule(newAllow, RuleKind.ALLOW)
                newAllow = ""
            }
        }
        rules(state.rules.filter { it.kind == RuleKind.ALLOW }, onRemoveRule)

        section("Chặn thêm (tuỳ chỉnh)")
        item {
            InputRow(newBlock, { newBlock = it }, "vd: tracker.example.com", "Thêm") {
                onAddRule(newBlock, RuleKind.BLOCK)
                newBlock = ""
            }
        }
        rules(state.rules.filter { it.kind == RuleKind.BLOCK }, onRemoveRule)
        item { Spacer(Modifier.height(72.dp)) }
    }
}

private fun LazyListScope.section(title: String) = item {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

private fun LazyListScope.rules(rules: List<CustomRule>, onRemove: (String) -> Unit) {
    if (rules.isEmpty()) {
        item { Text("Trống", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
    }
    items(rules, key = { "${it.kind}:${it.domain}" }) { rule ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(rule.domain, modifier = Modifier.weight(1f))
            TextButton(onClick = { onRemove(rule.domain) }) { Text("Xoá") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp))
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    button: String,
    keyboardType: KeyboardType = KeyboardType.Uri,
    onSubmit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onSubmit, enabled = value.isNotBlank()) { Text(button) }
    }
}

private fun presetName(upstream: UpstreamDns) = when (upstream) {
    UpstreamDns.Cloudflare -> "Cloudflare"
    UpstreamDns.Google -> "Google"
    UpstreamDns.Quad9 -> "Quad9"
    is UpstreamDns.Custom -> "Tuỳ chỉnh"
}
