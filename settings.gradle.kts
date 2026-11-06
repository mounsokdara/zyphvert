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
        // FFmpegKitNext (official continuation, arthenica) is source-distributed,
        // native .aar is built locally via scripts/build-ffmpeg-kit.sh and published
        // to this flatDir repo. See docs/FFMPEG.md before changing this.
        flatDir { dirs("libs") }
    }
}

rootProject.name = "Zyphvert"
include(":app")
