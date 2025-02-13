plugins {
    alias(baseLibs.plugins.conventionPlugin)

    alias(baseLibs.plugins.composeMultiplatform) apply false
    alias(baseLibs.plugins.composeCompiler) apply false
    alias(baseLibs.plugins.kotlinMultiplatform) apply false
    alias(baseLibs.plugins.mavenPublish) apply false
    alias(baseLibs.plugins.dokka)
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.buildconfig) apply false
}