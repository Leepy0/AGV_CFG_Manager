// :app Android 앱. 패키지 kr.agv.cfgmanager (임시, docs/DECISIONS.md 참고).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 빌드 값은 -P 로 덮어쓴다. CI 는 태그에서 versionName/versionCode 를 계산해 넘긴다 (docs/DEVELOPMENT_PLAN.md 12장).
//   ./gradlew :app:assembleDebug -Pchannel=dev -PversionName=1.0.0 -PversionCode=10000
val appChannel: String = providers.gradleProperty("channel").orElse("local").get()
val appVersionName: String = providers.gradleProperty("versionName").orElse("1.1.6").get()
val appVersionCode: Int = providers.gradleProperty("versionCode").orElse("1.1.6").get().toInt()
// 커밋 식별자 (정보 화면). CI 는 -PsourceRevision=<짧은 sha> 를 넘긴다. 로컬 빌드는 "local".
val appRevision: String = providers.gradleProperty("sourceRevision").orElse("local").get()

// 릴리스 서명 (docs/DEVELOPMENT_PLAN.md 12장). -P 나 환경변수로 넘긴다. 네 값이 모두 있으면 그 키로 서명한다.
// 하나라도 없으면 디버그 키로 서명한다 (docs/DECISIONS.md, 서명 방식 b). 디버그 키는 PC 마다 달라서,
// 다른 PC 에서 만든 APK 와는 서로 덮어 설치되지 않는다.
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }

val releaseKeystore: String? = signingValue("KEYSTORE_FILE")
val releaseStorePassword: String? = signingValue("KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = signingValue("KEY_ALIAS")
val releaseKeyPassword: String? = signingValue("KEY_PASSWORD")
val releaseSigningReady: Boolean = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }

android {
    namespace = "kr.agv.cfgmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.agv.cfgmanager"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "CHANNEL", "\"$appChannel\"")
        buildConfigField("String", "SOURCE_REVISION", "\"$appRevision\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // 축소 · 난독화는 ZXing 과 Keystore 검증을 실기로 확인하기 전까지 끈다 (docs/DECISIONS.md).
            isMinifyEnabled = false
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.zxing.android.embedded)
    implementation(project(":usbserial"))
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
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
