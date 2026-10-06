# VPNBlockADS

App chặn quảng cáo cho Android theo nguyên lý **DNS filtering qua `VpnService`** (giống AdAway / DNS66 / RethinkDNS). Dùng cá nhân, không phát hành Play Store.

```
App bất kỳ ──DNS query──▶ 10.111.222.2 (DNS giả) ──route /32──▶ TUN ──▶ AdBlockVpnService
                                                                         │
                         ┌───────────────────────────────────────────────┤
                         ▼                                               ▼
              domain trong blocklist?                          không bị chặn
              → tự trả NXDOMAIN (0ms)               → UDP socket đã protect() → 1.1.1.1
                         │                                               │
                         └──────── đóng gói IP/UDP (đảo src/dst) ◀───────┘
                                              │
                                    ghi ngược vào TUN → app nhận câu trả lời
```

Chỉ đúng địa chỉ DNS giả được route vào TUN. Mọi traffic khác (web, video…) vẫn đi mạng bình thường, nên app không ảnh hưởng tốc độ mạng và gần như không tốn pin.

## Build & chạy

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # toàn bộ unit test
./gradlew :core:dns:test         # chỉ phần lõi (chạy trên JVM, vài giây)
```

Môi trường: AGP 9.3 (built-in Kotlin), Kotlin 2.4.20, Gradle 9.5, JDK 17, compileSdk/targetSdk 37, minSdk 26.

## Cấu trúc module

| Module | Loại | Nội dung |
|---|---|---|
| `build-logic/convention` | Gradle | Convention plugins: `vpnblockads.android.application/library/compose/feature`, `vpnblockads.hilt`, `vpnblockads.jvm.library` |
| `:core:model` | JVM | Model thuần: `QueryLogEntry`, `CustomRule`, `AppSettings`, `VpnStatus`… |
| `:core:dns` | **JVM** | Lõi: parse/đóng gói IPv4-UDP, checksum, DNS wire format, `DomainFilter`, `HostsParser`, `DnsCache`, `UdpDnsUpstream`, `DnsRequestHandler` |
| `:core:domain` | JVM | Interface repository + `VpnController` + use case |
| `:core:data` | Android | Room (log, quy tắc), DataStore (cài đặt), blocklist (assets + tải từ URL) |
| `:core:vpn` | Android | `AdBlockVpnService`, `TunDevice`, notification, `VpnControllerImpl` |
| `:feature:home/logs/settings` | Android | Compose UI + ViewModel |
| `:app` | Android | `Application`, `MainActivity`, navigation, theme |

`:core:dns` là module Kotlin/JVM thật, không phải Android library. Compiler đảm bảo phần này không gọi được API Android, nên toàn bộ logic packet/DNS test được trên JVM.

## Các khái niệm chính

### VpnService & TUN
- `VpnService.Builder` tạo một **TUN interface**, tức là card mạng ảo. Mỗi lần `read()` trên fd của TUN trả về **một gói IP** mà app nào đó gửi vào route của VPN. Mỗi lần `write()` một gói IP thì kernel coi như gói đó vừa đến từ mạng và giao cho app đích.
- `addDnsServer(10.111.222.2)` khiến hệ thống dùng DNS này. `addRoute(10.111.222.2, 32)` khiến **chỉ** địa chỉ đó đi vào TUN.
- `allowFamily(AF_INET6)`: nếu VPN không khai báo gì cho IPv6, Android **chặn toàn bộ IPv6**. Gọi hàm này để IPv6 đi mạng như bình thường.
- `protect(socket)`: socket gửi lên upstream không bị route ngược vào TUN, tránh vòng lặp. `addDisallowedApplication(packageName)` loại trừ toàn bộ app khỏi VPN, ví dụ khi tải blocklist.
- `VpnService.prepare()` trả về Intent nếu chưa có quyền. Chỉ Activity mới hiện được hộp thoại xin quyền.

### Đọc TUN không bị treo khi dừng
`read()` trên TUN chặn luồng, và đóng fd từ luồng khác không chắc đánh thức được nó. `TunDevice` xử lý bằng cách dùng `Os.poll()` chờ đồng thời trên fd TUN và một **pipe**. Khi cần dừng, chỉ việc đóng đầu ghi của pipe, `poll` trả về ngay và vòng đọc thoát. Fd TUN được đóng trong `invokeOnCompletion` của coroutine đọc, nên không bao giờ đóng fd trong lúc đang có `read()`.

### Cấu trúc gói (xem chú thích trong `PacketParser.kt`, `DnsMessage.kt`)
- **IPv4**: header 20–60 byte (đọc IHL để biết độ dài, có thể có options), total length, protocol (17 = UDP), checksum header, src/dst.
- **UDP**: 8 byte gồm src port, dst port (53), length, checksum. Checksum tính trên một *pseudo header* gồm src/dst IP, protocol và length.
- **DNS**: header 12 byte (ID, flags QR/OPCODE/RD/RA/RCODE, 4 bộ đếm), question (QNAME dạng chuỗi label, QTYPE, QCLASS), rồi các resource record. Tên có thể **nén** bằng con trỏ `0xC0xx`.
- Response: đảo src/dst IP và port, tính lại cả **IP checksum lẫn UDP checksum**. UDP checksum là tuỳ chọn với IPv4 nhưng bắt buộc với IPv6, nên viết sẵn.

### Foreground service (Android 14+)
Mọi FGS phải khai báo `foregroundServiceType` kèm quyền tương ứng. Ở đây dùng `specialUse` với `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` (`FOREGROUND_SERVICE_SPECIAL_USE`). Đã kiểm tra trên emulator: `types=0x40000000`. Notification dùng kênh `IMPORTANCE_LOW` (không kêu, nằm ở nhóm Silent), có nút **Tắt**. Khi tắt từ notification thì hiện notification **Bật** để bật lại.

### Concurrency
- Luồng đọc chỉ parse gói (rất nhanh), mỗi truy vấn được xử lý trong một coroutine riêng trên `Dispatchers.IO`. Số truy vấn đồng thời tối đa là 64 (`Semaphore.tryAcquire`); quá tải thì bỏ gói, resolver của app sẽ tự hỏi lại.
- **Single writer**: chỉ một coroutine ghi vào TUN. Các truy vấn gửi kết quả qua `Channel`.
- Upstream: mỗi truy vấn dùng một socket mới (an toàn khi đổi Wi-Fi ↔ 4G). Sau 1,2s chưa có trả lời thì **gửi lại**, tổng timeout 4s. Hết thời gian hoặc lỗi thì trả **SERVFAIL**, để app không phải tự chờ hết timeout.
- `filter` và `settings` là snapshot bất biến `@Volatile`. Khi đổi quy tắc hay upstream thì thay cả tham chiếu, nên có hiệu lực ngay mà không cần khởi động lại VPN.

### Lọc domain
- Khớp domain cha: `a.b.ads.com` lần lượt kiểm tra `a.b.ads.com`, `b.ads.com`, `ads.com`. Không bao giờ xét TLD như `com`. `notads.com` **không** khớp với `ads.com`.
- **Whitelist thắng blocklist**, áp dụng cho cả subdomain.
- Chặn mọi loại truy vấn theo domain, kể cả HTTPS/SVCB (type 65), không chỉ A/AAAA.
- Khi VPN vừa bật mà blocklist chưa load xong, truy vấn **chờ** (tối đa 10s) thay vì được cho qua. Nếu cho qua, quảng cáo sẽ lọt, và netd còn cache câu trả lời "hợp lệ" đó theo TTL, nên domain không bị chặn thêm vài phút. Parser viết tay không dùng Regex, load 72k domain mất khoảng 3,6s trên emulator (bản debug), so với khoảng 10s khi dùng Regex.

### Cache DNS
Key gồm (tên, type, class). Khi trả từ cache, app **ghi lại ID** của truy vấn mới và **giảm TTL** theo thời gian đã nằm trong cache. Không cache SERVFAIL hay gói bị cắt (cờ TC). NXDOMAIN được cache theo SOA. Lưu ý: resolver `netd` của Android cũng tự cache theo TTL, nên cache của app chủ yếu giúp khi `netd` đã hết hạn hoặc khi có app tự gửi DNS.

### Log
Mỗi truy vấn sinh một log. Log được đẩy vào `Channel` rồi ghi Room **theo lô mỗi giây**, và tự xoá log cũ hơn 7 ngày khi VPN khởi động.

## Tự kiểm tra

1. Tắt **Private DNS** (Settings → Network → Private DNS → Off). Trong Chrome, tắt **Use secure DNS**.
2. Bật chặn ở tab Home.
3. Xem log trực tiếp:
   ```bash
   adb logcat -s AdBlockVpn
   ```
   Kết quả có dạng `BLOCKED A ad.doubleclick.net (0ms)` / `ALLOWED A github.com (25ms)`.
4. Phân giải thử từ shell:
   ```bash
   adb shell ping -c1 ad.doubleclick.net
   ```
   Kết quả mong đợi là `unknown host`, còn `example.com` vẫn phải phân giải được.
5. Trang test: `d3ward.github.io/toolz/adblock`, `canyoublockit.com`.
6. **PCAPdroid** (không cần root) giúp xem DNS gốc khi *chưa* bật app. Chỉ chạy được một VPN tại một thời điểm.
7. Đổi Wi-Fi ↔ 4G khi đang bật, bật/tắt liên tục nhiều lần: không được crash, mạng phải trở lại bình thường khi tắt.

## Giới hạn đã biết & hướng mở rộng
- App tự dùng DoH (Chrome Secure DNS, Firefox) hoặc hardcode `8.8.8.8` sẽ đi vòng qua bộ chặn. Cách mở rộng: route thêm IP các DNS công cộng phổ biến vào TUN.
- **IPv6**: thêm `PacketParser.parseIpv6` (header 40 byte, extension headers) và nhánh IPv6 trong `PacketBuilder`. `DnsQueryPacket.ipVersion` đã có sẵn.
- **DNS qua TCP**: hiện bị bỏ qua (`ParseResult.Ignored("tcp not supported")`). Cần một TCP stack tối giản, hoặc trả truncated rồi xử lý TCP.
- **DoH upstream**: thêm một `DnsUpstream` mới, không cần sửa phần xử lý gói.
- Quick Settings tile, WorkManager để tự cập nhật blocklist định kỳ.
