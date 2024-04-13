package com.mikepenz.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("kotlin")
        }

        configureKotlin()
    }
}
