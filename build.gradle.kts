plugins {
    id("java")
    id("com.diffplug.spotless") version "6.25.0" apply false
}

allprojects {
    group = "io.github.migraphe"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat("1.19.2").aosp().reflowLongStrings()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
