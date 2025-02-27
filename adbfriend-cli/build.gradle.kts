import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.mikepenz.convention.kotlin")
    id("application")
    id("com.mikepenz.convention.compose")
    alias(libs.plugins.shadow)
    alias(libs.plugins.buildconfig)
    alias(baseLibs.plugins.aboutLibraries)
}

kotlin {
    sourceSets {
        dependencies {
            implementation(projects.shared)
            implementation(libs.clikt)
            implementation(libs.slf4j)
            implementation(baseLibs.aboutlibraries.core) // aboutlibraries

            implementation(compose.runtime) { require(true) }
            implementation(compose.components.resources) {
                exclude("org.jetbrains.compose.foundation")
            }
        }
    }
}

application {
    applicationName = "adbfriend"
    mainModule = "com.mikepenz.adbfriend.app"
    mainClass = "com.mikepenz.adbfriend.MainKt"
    version = property("VERSION_NAME").toString()
}

buildConfig {
    buildConfigField("APP_VERSION", provider { "${project.version}" })
}

aboutLibraries {
    registerAndroidTasks = false
    duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
}

tasks.named("shadowJar", ShadowJar::class.java) {
    minimize {
        exclude(dependency("com.github.ajalt.mordant:.*:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
    }
}