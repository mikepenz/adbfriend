plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatform") {
            id = "com.mikepenz.kotlin.multiplatform"
            implementationClass = "com.mikepenz.gradle.KotlinMultiplatformConventionPlugin"
        }

        register("kotlin") {
            id = "com.mikepenz.kotlin"
            implementationClass = "com.mikepenz.gradle.KotlinConventionPlugin"
        }

        register("root") {
            id = "com.mikepenz.root"
            implementationClass = "com.mikepenz.gradle.RootConventionPlugin"
        }

        register("compose") {
            id = "com.mikepenz.compose"
            implementationClass = "com.mikepenz.gradle.ComposeMultiplatformConventionPlugin"
        }
    }
}
