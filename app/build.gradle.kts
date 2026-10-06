plugins {
    alias(libs.plugins.vpnblockads.android.application)
    alias(libs.plugins.vpnblockads.android.compose)
    alias(libs.plugins.vpnblockads.hilt)
}

android {
    namespace = "com.vpnblockads"

    defaultConfig {
        applicationId = "com.vpnblockads"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // App dùng cá nhân: ký release bằng debug key cho tiện cài đặt.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Không nén file hosts để đọc nhanh từ assets.
    androidResources {
        noCompress += "txt"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:vpn"))
    implementation(project(":feature:home"))
    implementation(project(":feature:logs"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}
