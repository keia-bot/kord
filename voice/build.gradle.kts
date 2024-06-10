plugins {
    `kord-multiplatform-module`
    `kord-publishing`
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets.commonMain.dependencies {
        api(projects.common)
        api(projects.gateway)

        implementation(libs.kotlin.logging)

        compileOnly(projects.kspAnnotations)
    }

    sourceSets.jsMain.dependencies {
        implementation(libs.kotlin.node)
    }

    sourceSets.nonJvmMain.dependencies {
        implementation(libs.libsodium)
    }

    sourceSets.jvmMain.dependencies {
        api(libs.ktor.network)
        implementation(libs.slf4j.api)
    }
}
