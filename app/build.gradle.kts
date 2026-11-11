import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Single source of truth for the app version: /version.txt (bump it, nothing else).
val versionProps = Properties().apply {
    rootProject.file("version.txt").inputStream().use { load(it) }
}

val keystorePropsFile = rootProject.file("key.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(FileInputStream(keystorePropsFile))
}

val ffmpegAar = file("libs/ffmpeg-kit-next.aar")

// CI builds one APK per job: ./gradlew assembleX -PtargetAbi=arm64-v8a
// Unset (local builds, universal job) keeps the normal 3 ABIs + universal split setup.
val targetAbi: String? = (findProperty("targetAbi") as String?)?.takeIf { it.isNotBlank() }
val allAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
require(targetAbi == null || targetAbi in allAbis) { "targetAbi must be one of $allAbis, got $targetAbi" }

android {
    // Real FFmpeg engine only when the AAR is present; otherwise a stub so the app still builds.
    sourceSets {
        getByName("main") {
            java.srcDir(if (ffmpegAar.exists()) "src/engineReal/kotlin" else "src/engineStub/kotlin")
        }
    }
    namespace = "com.mounsokdara.zyphvert"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mounsokdara.zyphvert"
        minSdk = 24
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE").trim().toInt()
        versionName = versionProps.getProperty("VERSION_NAME").trim()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // AGP forbids ndk.abiFilters together with ABI splits. So: single-ABI build (targetAbi set)
        // filters here and disables splits below; the default build uses splits only.
        if (targetAbi != null) {
            ndk { abiFilters += targetAbi }
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Debug is switched off: no debuggable app, no ".debug" id. CI and releases only build `release`.
        debug {
            isDebuggable = false
        }
    }

    splits {
        abi {
            // Single-ABI build (targetAbi set): no splits, one APK that contains just that ABI.
            isEnable = targetAbi == null
            reset()
            include(*allAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    packaging {
        // 16 KB page size alignment for native libs (Android 15+ target requirement)
        jniLibs { useLegacyPackaging = false }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Shizuku: lets the app read Android/data and Android/obb on Android 11+
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // FFmpegKitNext: built locally from source (official arthenica continuation).
    // See docs/FFMPEG.md. Placeholder module ref until scripts/build-ffmpeg-kit.sh
    // has produced libs/ffmpeg-kit-next-release.aar in CI.
    // Built by .github/workflows/ffmpeg-aar.yml, fetched by scripts/fetch-ffmpeg-aar.sh.
    // Only wired in once the file exists, so builds stay green before the first AAR is published.
    if (ffmpegAar.exists()) {
        implementation(files(ffmpegAar))
        implementation("com.arthenica:smart-exception-java:0.2.1")
    }

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
