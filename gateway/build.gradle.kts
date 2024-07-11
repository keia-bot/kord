plugins {
    `kord-multiplatform-module`
    `kord-publishing`
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.common)

        api(libs.bundles.ktor.client.serialization)
        api(libs.ktor.client.websockets)
        api(libs.ktor.websockets)

        implementation(libs.kotlin.logging)

        compileOnly(projects.kspAnnotations)
    }

    sourceSets.jvmMain.dependencies {
        implementation(libs.slf4j.api)
    }

    sourceSets.jsMain.dependencies {
        implementation(libs.kotlin.node)
        implementation(npm("fast-zlib", libs.versions.fastZlib.get()))

        // workaround for https://youtrack.jetbrains.com/issue/KT-43500
        // (intended to be compileOnly in commonMain only)
        implementation(projects.kspAnnotations)
    }
}
