import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.*

plugins {
    id("com.mikepenz.kotlin.multiplatform")
    id("com.mikepenz.compose")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.aboutlibraries)
}

if (appSigningFile != null) {
    apply(from = appSigningFile)
}

compose {
    kotlinCompilerPlugin.set(libs.versions.jetpackcompose.compiler.get())
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()

    sourceSets {

        val commonMain by getting {
            dependencies {
                implementation(projects.shared)
            }
        }

        jvmMain.dependencies {
            implementation(compose.runtime) { require(true) }
            implementation(compose.foundation) { require(true) }
            implementation(compose.material3) { require(true) }
            implementation(compose.ui) { require(true) }
            implementation(compose.components.resources) { require(true) }
            implementation(compose.desktop.currentOs)

            implementation(libs.bundles.aboutlibs) // aboutlibraries
        }
    }
}

compose.desktop {
    application {
        //from(sourceSets.)

        mainClass = "MainKt"

        buildTypes.release.proguard {
            isEnabled = true
            optimize = true
            obfuscate = true
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ADBFriend"
            packageVersion = "1.0.0"
            description = ""
            copyright = "© 2024 Mike Penz. All rights reserved."
        }
    }
}

aboutLibraries {
    registerAndroidTasks = false
    duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
}

private val appSigningFile: String?
    get() {
        val k = "signing.file"
        return Properties().also { prop ->
            rootProject.file("local.properties").takeIf { it.exists() }?.let {
                prop.load(it.inputStream())
            }
        }.getProperty(k, null) ?: if (project.hasProperty(k)) project.property(k)?.toString() else null
    }


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        if (project.findProperty("composeCompilerReports") == "true") {
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.layout.buildDirectory.asFile.get().absolutePath}/compose_compiler"
            )
        }
        if (project.findProperty("composeCompilerMetrics") == "true") {
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.layout.buildDirectory.asFile.get().absolutePath}/compose_compiler"
            )
        }
    }
}