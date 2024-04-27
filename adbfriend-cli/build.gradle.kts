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
            implementation("org.slf4j:slf4j-nop:2.0.6")
            implementation(libs.aboutlibraries.core) // aboutlibraries
        }
    }
}


application {
    mainClass = "com.mikepenz.adbfriend.MainKt"
    version = "0.1.0"
}

tasks.named("shadowJar", ShadowJar::class.java) {
    minimize {
        exclude(dependency("org.slf4j:.*:.*"))
    }
}