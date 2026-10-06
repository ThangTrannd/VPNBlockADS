plugins {
    alias(libs.plugins.vpnblockads.android.library)
    alias(libs.plugins.vpnblockads.hilt)
}

android {
    namespace = "com.vpnblockads.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:dns"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
