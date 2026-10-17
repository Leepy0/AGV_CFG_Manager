pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android 는 Maven Central 에 없다 (docs/DECISIONS.md, Phase 7).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AgvCfgManager"

include(":core")
include(":app")
