import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    `kord-multiplatform-module`
    `kord-publishing`
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("ktor") {
                withJvm()
                withApple()
                withLinux()
            }

            group("nonKtor") {
                withJs()
                withMingw()
            }
        }
    }

    jvm {
        withJava()
    }

    sourceSets.commonMain.dependencies {
        api(projects.common)
        api(projects.gateway)

        implementation(libs.kotlin.logging)

        compileOnly(projects.kspAnnotations)
    }

    sourceSets.named("ktorMain").dependencies {
        implementation(libs.ktor.network)
    }

    sourceSets.jsMain.dependencies {
        implementation(libs.kotlin.node)
    }

    sourceSets.nonJvmMain.dependencies {
        implementation(libs.libsodium)
    }

    sourceSets.jvmMain.dependencies {
        implementation(libs.slf4j.api)
    }
}

//
//tasks.withType<JavaCompile> {
//    sourceCompatibility = Jvm.targetInt.toString()
//
//    targetCompatibility = Jvm.targetInt.toString()
//}
