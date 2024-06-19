import org.jetbrains.dokka.gradle.AbstractDokkaLeafTask

plugins {
    org.jetbrains.kotlin.multiplatform
    // workaround for https://youtrack.jetbrains.com/issue/KT-43500 (not intended to be published)
    org.jetbrains.dokka
    `kord-publishing`
}

kotlin {
    targets()

//    sourceSets.commonMain.dependencies {
//        implementation(kotlin("stdlib", "2.0.0"))
//    }
}


tasks.withType<AbstractDokkaLeafTask>().configureEach {
    dokkaSourceSets.configureEach {
        suppress = true
    }
}
