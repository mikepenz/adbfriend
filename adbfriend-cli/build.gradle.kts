import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.mikepenz.kotlin")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    sourceSets {
        dependencies {
            implementation(projects.shared)
            implementation("com.github.ajalt.clikt:clikt:4.3.0")
            implementation(libs.aboutlibraries.core) // aboutlibraries
        }
    }
}


application {
    mainClass = "com.mikepenz.adbfriend.MainKt"
    version = "0.0.2"
}

tasks.named("shadowJar", ShadowJar::class.java) {
    minimize()
}