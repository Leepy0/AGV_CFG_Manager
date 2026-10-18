// :app Android ?? ?⑦궎吏 kr.agv.cfgmanager (?꾩떆, docs/DECISIONS.md 李멸퀬).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 鍮뚮뱶 媛믪? -P 濡???뼱?대떎. CI ???쒓렇?먯꽌 versionName/versionCode 瑜?怨꾩궛???섍릿??(docs/DEVELOPMENT_PLAN.md 12??.
//   ./gradlew :app:assembleDebug -Pchannel=dev -PversionName=1.0.0 -PversionCode=10000
val appChannel: String = providers.gradleProperty("channel").orElse("local").get()
val appVersionName: String = providers.gradleProperty("versionName").orElse("1.1.2").get()
val appVersionCode: Int = providers.gradleProperty("versionCode").orElse("1.1.2").get().toInt()
// 而ㅻ컠 ?앸퀎??(?뺣낫 ?붾㈃). CI ??-PsourceRevision=<吏㏃? sha> 瑜??섍릿?? 濡쒖뺄 鍮뚮뱶??"local".
val appRevision: String = providers.gradleProperty("sourceRevision").orElse("local").get()

// 由대━???쒕챸 (docs/DEVELOPMENT_PLAN.md 12??. -P ???섍꼍蹂?섎줈 ?섍릿?? ??媛믪씠 紐⑤몢 ?덉쑝硫?洹??ㅻ줈 ?쒕챸?쒕떎.
// ?섎굹?쇰룄 ?놁쑝硫??붾쾭洹??ㅻ줈 ?쒕챸?쒕떎 (docs/DECISIONS.md, ?쒕챸 諛⑹떇 b). ?붾쾭洹??ㅻ뒗 PC 留덈떎 ?щ씪??
// ?ㅻⅨ PC ?먯꽌 留뚮뱺 APK ????쒕줈 ??뼱 ?ㅼ튂?섏? ?딅뒗??
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
            // 異뺤냼 쨌 ?쒕룆?붾뒗 ZXing 怨?Keystore 寃利앹쓣 ?ㅺ린濡??뺤씤?섍린 ?꾧퉴吏 ?덈떎 (docs/DECISIONS.md).
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
