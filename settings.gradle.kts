rootProject.name = "adbfriend-root"

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }

    versionCatalogs {
        create("baseLibs") {
            from("com.mikepenz:version-catalog:0.1.4")
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// https://docs.gradle.org/7.6/userguide/configuration_cache.html#config_cache:stable
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
    ":shared", ":adbfriend", ":adbfriend-cli",
)
