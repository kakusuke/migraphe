rootProject.name = "migraphe"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // Versions
            version("junit", "5.10.1")
            version("assertj", "3.25.1")
            version("spotless", "6.25.0")
            version("googleJavaFormat", "1.19.2")
            version("postgresql", "42.7.1")
            version("testcontainers", "1.19.3")
            version("jackson", "2.18.2")
            version("picocli", "4.7.7")
            version("microprofile-config", "3.1")
            version("smallrye-config", "3.9.1")
            version("errorprone", "2.24.1")
            version("nullaway", "0.10.26")
            version("jspecify", "0.3.0")

            // Libraries
            library("junit-bom", "org.junit", "junit-bom").versionRef("junit")
            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
            library(
                    "junit-platform-launcher",
                    "org.junit.platform",
                    "junit-platform-launcher"
                )
                .withoutVersion()
            library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj")

            // PostgreSQL Plugin Dependencies
            library("postgresql", "org.postgresql", "postgresql").versionRef("postgresql")
            library("testcontainers-bom", "org.testcontainers", "testcontainers-bom")
                .versionRef("testcontainers")
            library("testcontainers-postgresql", "org.testcontainers", "postgresql")
                .withoutVersion()
            library("testcontainers-junit-jupiter", "org.testcontainers", "junit-jupiter")
                .withoutVersion()

            // CLI Dependencies
            library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind")
                .versionRef("jackson")
            library(
                    "jackson-dataformat-toml",
                    "com.fasterxml.jackson.dataformat",
                    "jackson-dataformat-toml"
                )
                .versionRef("jackson")
            library("picocli", "info.picocli", "picocli").versionRef("picocli")

            // MicroProfile Config
            library(
                    "microprofile-config-api",
                    "org.eclipse.microprofile.config",
                    "microprofile-config-api"
                )
                .versionRef("microprofile-config")
            library("smallrye-config", "io.smallrye.config", "smallrye-config")
                .versionRef("smallrye-config")
            library(
                    "smallrye-config-source-yaml",
                    "io.smallrye.config",
                    "smallrye-config-source-yaml"
                )
                .versionRef("smallrye-config")

            // Error Prone / NullAway
            library("errorprone-core", "com.google.errorprone", "error_prone_core")
                .versionRef("errorprone")
            library("nullaway", "com.uber.nullaway", "nullaway").versionRef("nullaway")
            library("jspecify", "org.jspecify", "jspecify").versionRef("jspecify")

            // Plugins
            plugin("spotless", "com.diffplug.spotless").versionRef("spotless")
            plugin("errorprone", "net.ltgt.errorprone").version("4.0.1")
        }
    }
}

include("migraphe-api")
include("migraphe-core")
include("migraphe-plugin-postgresql")
include("migraphe-cli")
include("migraphe-gradle-plugin")
