import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * AGP 9 đã tích hợp sẵn Kotlin (built-in Kotlin) nên không cần apply
 * `org.jetbrains.kotlin.android` nữa.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = AppConfig.COMPILE_SDK
            defaultConfig {
                minSdk = AppConfig.MIN_SDK
                targetSdk = AppConfig.TARGET_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.isReturnDefaultValues = true
        }
        configureKotlinJvmTarget()
        addUnitTestDependencies()
    }
}
