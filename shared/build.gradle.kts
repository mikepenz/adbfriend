plugins {
    id("com.mikepenz.kotlin.multiplatform")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm()

    sourceSets {

        val commonMain by getting {
            dependencies {
                api("com.malinskiy.adam:adam:0.5.5")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1-Beta")
            }
        }
    }
}