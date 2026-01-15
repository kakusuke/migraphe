plugins {
    `java-library`
}

dependencies {
    // API dependency only (no core implementation needed)
    implementation(project(":migraphe-api"))

    // PostgreSQL JDBC
    implementation(libs.postgresql)

    // MicroProfile Config (for @ConfigMapping)
    implementation(libs.smallrye.config)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.smallrye.config.source.yaml)

    // Testcontainers
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
