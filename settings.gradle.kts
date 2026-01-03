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

            // Plugins
            plugin("spotless", "com.diffplug.spotless").versionRef("spotless")
        }
    }
}

include("migraphe-core")
include("migraphe-postgresql")
include("migraphe-cli")
