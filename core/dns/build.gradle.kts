// Lõi xử lý gói IP/UDP/DNS + logic lọc domain. Kotlin thuần, không phụ thuộc Android.
plugins {
    alias(libs.plugins.vpnblockads.jvm.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    // dnsjava chỉ dùng trong test để tạo/kiểm tra DNS message chuẩn,
    // code chạy thật tự parse wire format (xem DnsMessage.kt).
    testImplementation(libs.dnsjava)
}
