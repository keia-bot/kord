plugins {
    org.jetbrains.kotlin.jvm
    dev.kord.`gradle-tools`
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(Jvm.targetInt)
    compilerOptions {
        jvmTarget = Jvm.targetVal
        applyKordCompilerOptions()
    }
}

tasks.compileJava {
    sourceCompatibility = Jvm.targetInt.toString()

    targetCompatibility = Jvm.targetInt.toString()
}
