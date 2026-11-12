import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val versionProps = Properties().apply {
    rootProject.file("version.txt").inputStream().use { load(it) }
}
val verName = versionProps.getProperty("VERSION_NAME")?.trim().orEmpty()
val verCode = versionProps.getProperty("VERSION_CODE")?.trim()?.toIntOrNull()
require(Regex("""\d+\.\d+\.\d+""").matches(verName)) { "version.txt: VERSION_NAME must look like 1.2.3, got '$verName'" }
require(verCode != null && verCode > 0) { "version.txt: VERSION_CODE must be a positive integer" }

val keystorePropsFile = rootProject.file("key.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(FileInputStream(keystorePropsFile))
}

val ffmpegAar = file("libs/ffmpeg-kit-next.aar")

val targetAbi: String? = (findProperty("targetAbi") as String?)?.takeIf { it.isNotBlank() }
val allAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
require(targetAbi == null || targetAbi in allAbis) { "targetAbi must be one of $allAbis, got $targetAbi" }

android {
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
        // One version for every APK/AAB: straight from version.txt, no per-ABI multiplier.
        versionCode = verCode
        versionName = verName
        resourceConfigurations += "en"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

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
        debug {
            isDebuggable = false
        }
    }

    splits {
        abi {
            isEnable = targetAbi == null
            reset()
            include(*allAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    packaging {
        // Compressed native libs: much smaller APK (libs are extracted at install).
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/proguard/**",
                "/META-INF/com.android.tools/**",
                "/META-INF/versions/**",
                "/META-INF/maven/**",
                "/kotlin/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
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
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

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
