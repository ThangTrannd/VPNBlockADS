package com.vpnblockads.feature.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnblockads.core.model.BlocklistStatus
import com.vpnblockads.core.model.VpnStatus

@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Bước 2: xin quyền VPN. VpnService.prepare() trả Intent nếu chưa có quyền -> hệ thống hiện hộp thoại.
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.start()
    }
    val startWithVpnPermission = {
        val intent = VpnService.prepare(context)
        if (intent != null) vpnPermission.launch(intent) else viewModel.start()
    }

    // Bước 1 (Android 13+): xin quyền hiện notification. Từ chối vẫn chạy được, chỉ không thấy notification.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startWithVpnPermission()
    }

    HomeScreen(
        state = state,
        onToggle = {
            when (state.status) {
                is VpnStatus.Running, VpnStatus.Starting -> viewModel.stop()
                else -> {
                    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsNotificationPermission) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startWithVpnPermission()
                    }
                }
            }
        },
    )
}

@Composable
fun HomeScreen(state: HomeUiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val running = state.status is VpnStatus.Running
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = 6.dp,
            modifier = Modifier.size(180.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (state.status == VpnStatus.Starting) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(if (running) "BẬT" else "TẮT", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(statusText(state.status), style = MaterialTheme.typography.titleMedium)
        (state.status as? VpnStatus.Error)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Đã chặn hôm nay", state.blockedToday.toString(), Modifier.weight(1f))
            StatCard("Truy vấn hôm nay", state.queriesToday.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        StatCard(
            title = "Blocklist",
            value = if (state.blocklist.isLoading) "Đang tải…" else "%,d domain".format(state.blocklist.domainCount),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun statusText(status: VpnStatus) = when (status) {
    is VpnStatus.Running -> "Đang chặn quảng cáo"
    VpnStatus.Starting -> "Đang khởi động…"
    VpnStatus.Stopped -> "Đã tắt — chạm để bật"
    is VpnStatus.Error -> "Lỗi"
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    MaterialTheme {
        HomeScreen(
            HomeUiState(VpnStatus.Running(0), blockedToday = 128, queriesToday = 1024, blocklist = BlocklistStatus(72_000)),
            onToggle = {},
        )
    }
}
