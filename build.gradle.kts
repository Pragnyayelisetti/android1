// PatchCam Android build configuration
// Compatible with current Android Studio / Gradle 9.x.
//
// AGP 9 provides built-in Kotlin, so the old
// org.jetbrains.kotlin.android plugin is intentionally not applied.

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.chaquo.python") version "17.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
