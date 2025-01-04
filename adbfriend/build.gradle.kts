import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.*

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.compose")
    alias(baseLibs.plugins.aboutLibraries)
}

if (appSigningFile != null) {
    apply(from = appSigningFile)
}

kotlin {
    sourceSets {

        val commonMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(compose.components.resources)
            }
        }
        jvmMain.dependencies {
            implementation(compose.runtime) { require(true) }
            implementation(compose.foundation) { require(true) }
            implementation(compose.material3) { require(true) }
            implementation(compose.ui) { require(true) }
            implementation(compose.desktop.currentOs)

            implementation(baseLibs.bundles.aboutlibs) // aboutlibraries
        }
    }
}

compose.desktop {
    application {
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
            packageVersion = property("VERSION_NAME").toString()
            description = ""
            copyright = "© 2025 Mike Penz. All rights reserved."
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
