package com.vpnblockads.feature.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnblockads.core.model.DnsRecordTypes
import com.vpnblockads.core.model.QueryStatus
import com.vpnblockads.core.model.RuleKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun LogsRoute(viewModel: LogsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        LogsScreen(
            state = state,
            onFilterChange = viewModel::setFilter,
            onItemClick = { selected = it },
            onClear = viewModel::clearLogs,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    selected?.let { domain ->
        val rule = state.items.firstOrNull { it.entry.domain == domain }?.rule
        RuleDialog(
            domain = domain,
            currentRule = rule,
            onDismiss = { selected = null },
            onAllow = { viewModel.addRule(domain, RuleKind.ALLOW); selected = null },
            onBlock = { viewModel.addRule(domain, RuleKind.BLOCK); selected = null },
            onRemove = { viewModel.removeRule(domain); selected = null },
        )
    }
}

@Composable
fun LogsScreen(
    state: LogsUiState,
    onFilterChange: (LogFilter) -> Unit,
    onItemClick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LogFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            when (filter) {
                                LogFilter.ALL -> "Tất cả"
                                LogFilter.BLOCKED -> "Bị chặn"
                                LogFilter.ALLOWED -> "Cho qua"
                            },
                        )
                    },
                )
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Xoá log") }
        }
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có truy vấn nào.\nBật chặn ở tab Home rồi dùng điện thoại bình thường.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.entry.id }) { item ->
                    LogRow(item, onClick = { onItemClick(item.entry.domain) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(item: LogItem, onClick: () -> Unit) {
    val entry = item.entry
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.domain, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis)))
                    append(" · ").append(DnsRecordTypes.name(entry.type))
                    append(" · ").append(entry.latencyMillis).append("ms")
                    item.rule?.let { append(if (it == RuleKind.ALLOW) " · whitelist" else " · chặn thủ công") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusLabel(entry.status)
    }
}

@Composable
private fun StatusLabel(status: QueryStatus) {
    val (text, color) = when (status) {
        QueryStatus.BLOCKED -> "Chặn" to MaterialTheme.colorScheme.error
        QueryStatus.WHITELISTED -> "Whitelist" to MaterialTheme.colorScheme.tertiary
        QueryStatus.CACHED -> "Cache" to MaterialTheme.colorScheme.secondary
        QueryStatus.FAILED -> "Lỗi" to Color(0xFFE65100)
        QueryStatus.ALLOWED -> "OK" to MaterialTheme.colorScheme.primary
    }
    Text(text, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun RuleDialog(
    domain: String,
    currentRule: RuleKind?,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(domain) },
        text = {
            Column {
                Text("Quy tắc áp dụng cho domain này và mọi subdomain của nó.")
                TextButton(onClick = onAllow, enabled = currentRule != RuleKind.ALLOW) { Text("Luôn cho phép (whitelist)") }
                TextButton(onClick = onBlock, enabled = currentRule != RuleKind.BLOCK) { Text("Luôn chặn") }
                if (currentRule != null) TextButton(onClick = onRemove) { Text("Xoá quy tắc hiện tại") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}
