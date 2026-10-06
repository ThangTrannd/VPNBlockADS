plugins {
    alias(libs.plugins.vpnblockads.android.library)
    alias(libs.plugins.vpnblockads.hilt)
}

android {
    namespace = "com.vpnblockads.core.vpn"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:dns"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
}
