plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()

    sourceSets {

        val commonMain by getting {
            dependencies {
                api(libs.adam)
                api(baseLibs.kotlin.coroutines.core)
            }
        }
    }
}