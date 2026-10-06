plugins {
    alias(libs.plugins.vpnblockads.android.feature)
}

android {
    namespace = "com.vpnblockads.feature.home"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
