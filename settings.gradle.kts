rootProject.name = "migraphe"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // Versions
            version("junit", "5.10.1")
            version("assertj", "3.25.1")
            version("spotless", "6.25.0")
            version("googleJavaFormat", "1.19.2")

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

            // Plugins
            plugin("spotless", "com.diffplug.spotless").versionRef("spotless")
        }
    }
}

include("migraphe-core")
