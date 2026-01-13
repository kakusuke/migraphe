import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java")
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
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
    apply(plugin = "net.ltgt.errorprone")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "errorprone"(rootProject.libs.errorprone.core)
        "errorprone"(rootProject.libs.nullaway)
        "compileOnly"(rootProject.libs.jspecify)
        "testCompileOnly"(rootProject.libs.jspecify)
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp().reflowLongStrings()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            // Enable NullAway only for main source code, not test code
            if (name == "compileJava") {
                error("NullAway")
            } else {
                disable("NullAway")
            }
            option("NullAway:AnnotatedPackages", "io.github.migraphe")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
