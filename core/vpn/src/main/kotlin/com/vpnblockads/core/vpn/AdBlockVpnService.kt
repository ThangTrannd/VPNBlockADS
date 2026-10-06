package com.vpnblockads.core.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.ServiceCompat
import com.vpnblockads.core.dns.DnsRequestHandler
import com.vpnblockads.core.dns.cache.DnsCache
import com.vpnblockads.core.dns.filter.DomainFilter
import com.vpnblockads.core.dns.packet.ParseResult
import com.vpnblockads.core.dns.packet.PacketParser
import com.vpnblockads.core.dns.upstream.UdpDnsUpstream
import com.vpnblockads.core.domain.repository.BlocklistRepository
import com.vpnblockads.core.domain.repository.CustomRuleRepository
import com.vpnblockads.core.domain.repository.QueryLogRepository
import com.vpnblockads.core.domain.repository.SettingsRepository
import com.vpnblockads.core.model.AppSettings
import com.vpnblockads.core.model.DnsRecordTypes
import com.vpnblockads.core.model.QueryLogEntry
import com.vpnblockads.core.model.QueryStatus
import com.vpnblockads.core.model.RuleKind
import com.vpnblockads.core.model.VpnStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * VpnService: API cho phép app tạo một "card mạng ảo" (TUN) và chọn traffic nào đi qua nó.
 *
 * Ở đây ta chỉ khai báo một DNS server giả (10.111.222.2) và CHỈ route đúng địa chỉ đó
 * (/32) vào TUN. Kết quả: mọi truy vấn DNS của hệ thống chảy vào app, còn traffic
 * thật (web, video...) vẫn đi thẳng ra mạng — không tốn pin, không ảnh hưởng tốc độ.
 *
 * Luồng dữ liệu:
 * ```
 * TUN --read--> PacketParser --> DnsRequestHandler (coroutine/truy vấn) --> Channel --write--> TUN
 *                                     |-- bị chặn: tự trả NXDOMAIN
 *                                     |-- cho qua: UDP socket đã protect() --> 1.1.1.1
 * ```
 */
@AndroidEntryPoint
class AdBlockVpnService : VpnService() {

    @Inject lateinit var controller: VpnControllerImpl
    @Inject lateinit var blocklistRepository: BlocklistRepository
    @Inject lateinit var customRuleRepository: CustomRuleRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var queryLogRepository: QueryLogRepository

    private lateinit var notifications: VpnNotifications
    private val mainHandler = Handler(Looper.getMainLooper())

    // Trạng thái của một phiên chạy; chỉ truy cập trên main thread.
    private var scope: CoroutineScope? = null
    private var tun: TunDevice? = null

    // Đọc từ các coroutine xử lý DNS, ghi từ collector -> @Volatile, thay cả tham chiếu.
    @Volatile private var filter = DomainFilter.EMPTY
    @Volatile private var settings = AppSettings()
    private val cache = DnsCache()
    private val blockedInSession = AtomicInteger()
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        notifications = VpnNotifications(this).also { it.createChannel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn(error = null, fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false))
                return START_NOT_STICKY
            }
            // ACTION_START, null (hệ thống khởi động lại service), android.net.VpnService (always-on)
            else -> startVpn()
        }
        return START_STICKY
    }

    /** Gọi khi người dùng thu hồi quyền hoặc một app VPN khác chiếm chỗ. */
    override fun onRevoke() {
        stopVpn(error = null, fromNotification = false)
    }

    override fun onDestroy() {
        // Chỉ dọn khi còn phiên đang chạy, để không ghi đè trạng thái Error vừa đặt.
        if (scope != null) stopVpn(error = null, fromNotification = false, stopService = false)
        super.onDestroy()
    }

    /**
     * MỖI lần gọi startForegroundService() đều đòi service gọi startForeground() trong vài giây,
     * kể cả khi service đã chạy sẵn — nếu không app bị crash (ForegroundServiceDidNotStartInTime).
     */
    private fun goForeground() {
        ServiceCompat.startForeground(
            this,
            VpnNotifications.RUNNING_ID,
            notifications.running(blockedInSession.get()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun startVpn() {
        if (scope != null) {
            goForeground()
            return
        }

        // Phải gọi startForeground() sớm (trong vài giây sau startForegroundService) dù sau đó có lỗi.
        blockedInSession.set(0)
        goForeground()
        notifications.cancelStopped()

        // prepare() != null nghĩa là chưa có quyền (service không thể tự hiện hộp thoại xin quyền).
        if (prepare(this) != null) {
            notifications.showPermissionNeeded()
            stopVpn(error = getString(R.string.vpn_error_permission), fromNotification = false)
            return
        }

        controller.update(VpnStatus.Starting)
        val pfd = try {
            buildTun()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
            null
        }
        if (pfd == null) {
            stopVpn(error = getString(R.string.vpn_error_establish), fromNotification = false)
            return
        }

        val device = TunDevice(pfd)
        val newScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "coroutine failed", e) },
        )
        tun = device
        scope = newScope
        launchSession(newScope, device)
        controller.update(VpnStatus.Running(System.currentTimeMillis()))
        Log.i(TAG, "VPN started")
    }

    private fun buildTun(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .addAddress(VpnConstants.TUN_ADDRESS, VpnConstants.TUN_PREFIX)
            // Hệ thống sẽ dùng DNS server này cho mọi app...
            .addDnsServer(VpnConstants.FAKE_DNS)
            // ...và chỉ địa chỉ đó đi vào TUN; mọi traffic khác giữ nguyên đường cũ.
            .addRoute(VpnConstants.FAKE_DNS, 32)
            .setMtu(VpnConstants.MTU)
            .setBlocking(true)
            // Mặc định, họ địa chỉ nào VPN không khai báo (ở đây IPv6) sẽ bị CHẶN hoàn toàn.
            // allowFamily cho IPv6 đi mạng bình thường như khi không bật VPN.
            .allowFamily(OsConstants.AF_INET6)
        try {
            // Socket upstream của app đã protect(), nhưng loại trừ cả app cho chắc:
            // mọi kết nối của chính app (tải blocklist...) không bao giờ vòng qua TUN.
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "cannot exclude self", e)
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setConfigureIntent(
                android.app.PendingIntent.getActivity(this, 0, launch, android.app.PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return builder.establish() // null nếu quyền VPN vừa bị thu hồi
    }

    private fun launchSession(scope: CoroutineScope, device: TunDevice) {
        // --- Dữ liệu cấu hình: cập nhật nóng, không cần khởi động lại VPN ---
        // Bộ lọc chỉ "sẵn sàng" khi blocklist đã load xong. Trước thời điểm đó, nếu cho truy vấn
        // đi qua thì quảng cáo lọt VÀ resolver hệ thống (netd) còn cache câu trả lời đó theo TTL,
        // khiến domain không bị chặn thêm vài phút. Vì vậy truy vấn sẽ chờ (có giới hạn).
        val filterReady = CompletableDeferred<Unit>()
        scope.launch {
            blocklistRepository.ensureLoaded()
            combine(blocklistRepository.domains, customRuleRepository.rules) { base, rules ->
                DomainFilter(
                    blocked = listOf(base, rules.filter { it.kind == RuleKind.BLOCK }.mapTo(HashSet()) { it.domain }),
                    allowed = rules.filter { it.kind == RuleKind.ALLOW }.mapTo(HashSet()) { it.domain },
                )
            }.collect {
                filter = it
                if (filterReady.complete(Unit)) {
                    Log.i(TAG, "filter ready: ${blocklistRepository.status.value.domainCount} domains")
                }
            }
        }
        scope.launch {
            settingsRepository.settings.collect { new ->
                if (new.upstream != settings.upstream) cache.clear()
                settings = new
            }
        }
        scope.launch { queryLogRepository.deleteOlderThan(System.currentTimeMillis() - VpnConstants.LOG_RETENTION_MILLIS) }
        scope.launch { updateNotificationPeriodically() }

        // --- Đường đi của gói ---
        val handler = DnsRequestHandler(
            filterProvider = { filter },
            blockModeProvider = { settings.blockMode },
            upstream = UdpDnsUpstream(
                serverProvider = { InetSocketAddress(InetAddress.getByName(settings.upstream.address), VpnConstants.UPSTREAM_PORT) },
                protector = { socket -> protect(socket) },
            ),
            cache = cache,
            onQuery = ::onQuery,
        )

        // Single writer: chỉ coroutine này ghi vào TUN, các truy vấn gửi kết quả qua Channel.
        val outgoing = Channel<ByteArray>(capacity = 256)
        scope.launch {
            for (packet in outgoing) {
                try {
                    device.write(packet)
                } catch (e: IOException) {
                    Log.w(TAG, "write to tun failed", e)
                }
            }
        }

        val inFlight = Semaphore(VpnConstants.MAX_IN_FLIGHT)
        scope.launch {
            try {
                device.readLoop { buffer, length ->
                    // Parse ngay trên luồng đọc (rất nhanh, có copy dữ liệu ra khỏi buffer).
                    val result = PacketParser.parse(buffer, length)
                    if (result !is ParseResult.Dns) return@readLoop
                    // Không để truy vấn chậm làm nghẽn vòng đọc: mỗi truy vấn một coroutine,
                    // giới hạn số lượng; quá tải thì bỏ gói (resolver của app sẽ thử lại).
                    if (!inFlight.tryAcquire()) {
                        Log.w(TAG, "too many in-flight queries, dropping")
                        return@readLoop
                    }
                    scope.launch {
                        try {
                            // Quá hạn mà blocklist vẫn chưa xong thì cho qua (fail-open): thà lọt
                            // quảng cáo còn hơn làm cả máy mất DNS.
                            if (!filterReady.isCompleted) withTimeoutOrNull(VpnConstants.FILTER_WAIT_MILLIS) { filterReady.await() }
                            handler.handle(result.packet)?.let { outgoing.send(it) }
                        } finally {
                            inFlight.release()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "tun read loop failed", e)
            }
            // Vòng đọc thoát mà KHÔNG phải do ta dừng (lỗi, read() trả -1...) thì VPN vẫn
            // "trông như đang chạy" nhưng mọi DNS đều chết = mất mạng. Phải dừng hẳn và báo lỗi.
            if (!device.isStopRequested) {
                mainHandler.post {
                    if (this@AdBlockVpnService.scope === scope) {
                        stopVpn(error = getString(R.string.vpn_error_tun), fromNotification = false)
                    }
                }
            }
        }
        // Chỉ đóng fd TUN khi MỌI coroutine của phiên (reader, writer, các truy vấn) đã kết thúc.
        // Nếu đóng sớm, writer có thể đang write() vào một số fd đã bị hệ thống cấp lại cho
        // file/socket khác (ví dụ TUN của phiên mới khi bật/tắt nhanh).
        scope.coroutineContext.job.invokeOnCompletion { device.close() }
    }

    private fun onQuery(entry: QueryLogEntry) {
        if (entry.status == QueryStatus.BLOCKED) blockedInSession.incrementAndGet()
        Log.d(TAG, "${entry.status} ${DnsRecordTypes.name(entry.type)} ${entry.domain} (${entry.latencyMillis}ms)")
        queryLogRepository.record(entry)
    }

    private suspend fun updateNotificationPeriodically() {
        var shown = 0
        while (true) {
            delay(NOTIFICATION_UPDATE_MILLIS)
            val current = blockedInSession.get()
            if (current != shown) {
                shown = current
                notifications.updateRunning(current)
            }
        }
    }

    /** Dừng sạch: đánh thức vòng đọc, huỷ mọi coroutine, đóng TUN, gỡ notification. */
    private fun stopVpn(error: String?, fromNotification: Boolean, stopService: Boolean = true) {
        val currentScope = scope
        scope = null
        tun?.requestStop() // reader thoát; fd đóng khi mọi coroutine của phiên kết thúc
        tun = null
        currentScope?.cancel()
        if (currentScope != null) Log.i(TAG, "VPN stopped")

        controller.update(if (error != null) VpnStatus.Error(error) else VpnStatus.Stopped)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (fromNotification) notifications.showStopped()
        // stopSelf(startId): nếu đã có lệnh START mới hơn đang chờ thì KHÔNG dừng service,
        // tránh mất lệnh bật khi người dùng bấm tắt/bật liên tục.
        if (stopService) stopSelf(lastStartId)
    }

    companion object {
        private const val TAG = "AdBlockVpn"
        private const val NOTIFICATION_UPDATE_MILLIS = 5_000L
        private const val ACTION_START = "com.vpnblockads.action.START"
        private const val ACTION_STOP = "com.vpnblockads.action.STOP"
        private const val EXTRA_FROM_NOTIFICATION = "from_notification"

        fun startIntent(context: Context): Intent =
            Intent(context, AdBlockVpnService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context, fromNotification: Boolean = false): Intent =
            Intent(context, AdBlockVpnService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_FROM_NOTIFICATION, fromNotification)
    }
}
