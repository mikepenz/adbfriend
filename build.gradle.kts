plugins {
    id("com.mikepenz.root")

    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.mavenpublish) apply false
    alias(libs.plugins.dokka)
}