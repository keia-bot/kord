plugins {
    org.jetbrains.kotlin.jvm
    dev.kord.`gradle-tools`
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(Jvm.target)
    compilerOptions {
        applyKordCompilerOptions()
    }
}
