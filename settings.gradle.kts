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
            version("testcontainers", "2.0.3")
            version("jackson", "2.18.2")
            version("picocli", "4.7.7")
            version("microprofile-config", "3.1")
            version("smallrye-config", "3.9.1")
            version("errorprone", "2.24.1")
            version("nullaway", "0.10.26")
            version("jspecify", "0.3.0")
            version("h2", "2.3.232")

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
            library("testcontainers-postgresql", "org.testcontainers", "testcontainers-postgresql")
                .withoutVersion()
            library("testcontainers-junit-jupiter", "org.testcontainers", "testcontainers-junit-jupiter")
                .withoutVersion()

            // MySQL Plugin Dependencies
            version("mysql-connector-j", "9.2.0")
            library("mysql-connector-j", "com.mysql", "mysql-connector-j")
                .versionRef("mysql-connector-j")
            library("testcontainers-mysql", "org.testcontainers", "testcontainers-mysql")
                .withoutVersion()

            // JDBC Plugin Dependencies
            library("h2", "com.h2database", "h2").versionRef("h2")

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

            // SLF4J NOP (suppress Maven Resolver SLF4J warnings in CLI)
            library("slf4j-nop", "org.slf4j", "slf4j-nop").version("1.7.36")

            // Maven Resolver (CLI plugin dependency resolution)
            version("maven-resolver-provider", "3.9.9")
            version("maven-resolver", "1.9.22")
            library("maven-resolver-provider", "org.apache.maven", "maven-resolver-provider")
                .versionRef("maven-resolver-provider")
            library("maven-resolver-connector-basic", "org.apache.maven.resolver", "maven-resolver-connector-basic")
                .versionRef("maven-resolver")
            library("maven-resolver-transport-file", "org.apache.maven.resolver", "maven-resolver-transport-file")
                .versionRef("maven-resolver")
            library("maven-resolver-transport-http", "org.apache.maven.resolver", "maven-resolver-transport-http")
                .versionRef("maven-resolver")

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
include("migraphe-plugin-jdbc")
include("migraphe-plugin-postgresql")
include("migraphe-plugin-mysql")
include("migraphe-generator-api")
include("migraphe-cli")
include("migraphe-gradle-plugin")
include("migraphe-plugin-generator-json")
