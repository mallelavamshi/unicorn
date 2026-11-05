// Emulator test fixture only: a text field and an instrumentation that swipes words through Unicorn's keys with
// injected touches, for tools/glide-stress.sh. Never published.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.unicorn.glidetest"
    compileSdk = 36
    defaultConfig {
        applicationId = "ai.unicorn.glidetest"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "glidetest"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        // The same synthetic swipes and word lists as the JVM accuracy tests
        getByName("main") {
            java.srcDir("../app/src/glidesynth/kotlin")
            assets.srcDir("../app/src/test/resources/glide")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
