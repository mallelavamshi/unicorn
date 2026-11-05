import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    // The keyboard engine (AOSP LatinIME, Apache-2.0) keeps its original Java packages: its JNI symbols are bound to
    // them. The published application id is Unicorn's own.
    namespace = "com.android.inputmethod.latin"
    compileSdk = 36
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "ai.unicorn.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.5.2"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // Unicorn backend (Cloudflare Worker). Override with -PunicornApiUrl=... for local testing.
        buildConfigField(
            "String", "UNICORN_API_URL",
            "\"${project.findProperty("unicornApiUrl") ?: "https://unicorn-api.m-vamshikrishna22.workers.dev"}\"",
        )
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    androidResources {
        // Dictionaries are memory-mapped by the native engine and must stay uncompressed
        noCompress += "dict"
        // Unicorn ships English first; the engine's ~90 UI translations add megabytes for no benefit yet
        localeFilters += listOf("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    sourceSets {
        // Synthetic swipes, shared with the emulator glide test (glidetest/)
        getByName("test").java.srcDir("src/glidesynth/kotlin")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Keyboard engine
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    // Unicorn
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    val composeBom = platform("androidx.compose:compose-bom:2025.11.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.20")
}
