// usb-serial-for-android 3.11.0 (MIT, LICENSE.txt) 소스를 그대로 가져온 모듈.
// 사내망에서 JitPack 을 못 쓰므로 저장소에 둔다 (docs/DECISIONS.md, Phase 7). 바꾸지 않는다.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.hoho.android.usbserial"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}
