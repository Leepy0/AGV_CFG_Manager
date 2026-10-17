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
    }
}

rootProject.name = "AgvCfgManager"

include(":core")
include(":app")
include(":usbserial")
project(":usbserial").projectDir = file("third_party/usb-serial-for-android")
