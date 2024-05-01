plugins {
    id("com.mikepenz.kotlin.multiplatform")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()

    sourceSets {

        val commonMain by getting {
            dependencies {
                api(libs.adam)
                api(libs.kotlin.coroutines)
            }
        }
    }
}