plugins {
    org.jetbrains.dokka // for dokkaHtmlMultiModule task
}

allprojects {
    repositories {
//        mavenLocal()
        mavenCentral()
        maven("https://maven.dimensional.fun/releases")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

group = Library.group
