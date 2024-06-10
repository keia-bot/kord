plugins {
    org.jetbrains.kotlin.jvm
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
