import dev.kord.gradle.tools.util.commitHash
import dev.kord.gradle.tools.util.shortCommitHash

plugins {
    `kord-multiplatform-module`
    `kord-publishing`
    alias(libs.plugins.buildconfig)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
        api(libs.kotlinx.datetime)
        api(libs.ktor.client.core)

        compileOnly(projects.kspAnnotations)
    }

    sourceSets.jvmMain.dependencies {
        api(libs.ktor.client.java)
    }

    sourceSets.nonJvmMain.dependencies {
        implementation(libs.ktor.utils)
        implementation(libs.bignum)
        implementation(libs.stately.collections)
    }

    sourceSets.jsMain.dependencies {
        api(libs.ktor.client.js)

        // workaround for https://youtrack.jetbrains.com/issue/KT-43500
        // (intended to be compileOnly in commonMain only)
        implementation(projects.kspAnnotations)
    }

    sourceSets.nativeMain.dependencies {
        // Native does not have compileOnly
        implementation(projects.kspAnnotations)
    }

    sourceSets.mingwMain.dependencies {
        api(libs.ktor.client.winhttp)
    }

    sourceSets.appleMain.dependencies {
        api(libs.ktor.client.darwin)
    }

    sourceSets.linuxMain.dependencies {
        api(libs.ktor.client.curl)
    }

    sourceSets.jvmTest.dependencies {
        implementation(libs.bson)
        implementation(libs.kbson)
    }
}

/*
This will generate a file named "BuildConfigGenerated.kt" that looks like:

package dev.kord.common

internal const val BUILD_CONFIG_GENERATED_LIBRARY_VERSION: String = "<version>"
internal const val BUILD_CONFIG_GENERATED_COMMIT_HASH: String = "<commit hash>"
internal const val BUILD_CONFIG_GENERATED_SHORT_COMMIT_HASH: String = "<short commit hash>"
*/
buildConfig {
    packageName = "dev.kord.common"
    className = "BuildConfigGenerated"

    useKotlinOutput {
        topLevelConstants = true
        internalVisibility = true
    }

    buildConfigField("BUILD_CONFIG_GENERATED_LIBRARY_VERSION", provider { project.version.toString() })
    buildConfigField("BUILD_CONFIG_GENERATED_COMMIT_HASH", commitHash)
    buildConfigField("BUILD_CONFIG_GENERATED_SHORT_COMMIT_HASH", shortCommitHash)
}
