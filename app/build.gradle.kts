// :app Android 앱. 패키지 kr.agv.cfgmanager (임시, docs/DECISIONS.md 참고).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 빌드 값은 -P 로 덮어쓴다. CI 는 태그에서 versionName/versionCode 를 계산해 넘긴다 (docs/DEVELOPMENT_PLAN.md 12장).
//   ./gradlew :app:assembleDebug -Pchannel=dev -PversionName=0.1.0 -PversionCode=100
val appChannel: String = providers.gradleProperty("channel").orElse("local").get()
val appVersionName: String = providers.gradleProperty("versionName").orElse("0.1.0").get()
val appVersionCode: Int = providers.gradleProperty("versionCode").orElse("100").get().toInt()

android {
    namespace = "kr.agv.cfgmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.agv.cfgmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "CHANNEL", "\"$appChannel\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 릴리스 서명(keystore 는 GitHub Secrets)은 Phase 6 에서 추가한다.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
