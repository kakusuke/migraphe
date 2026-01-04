plugins {
    `java-library`
}

dependencies {
    // API module
    api(project(":migraphe-api"))

    // MicroProfile Config
    api(libs.microprofile.config.api)
    implementation(libs.smallrye.config)
    implementation(libs.smallrye.config.source.yaml)

    // JUnit 5
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // AssertJ
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
