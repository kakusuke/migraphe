plugins {
    `java-library`
}

dependencies {
    // API dependency - compileOnly because host application provides it at runtime
    compileOnly(project(":migraphe-api"))
    // API needed for tests
    testImplementation(project(":migraphe-api"))

    // Generator API
    api(project(":migraphe-generator-api"))

    // Jackson for JSON serialization
    implementation(libs.jackson.databind)

    // Null safety
    compileOnly(libs.jspecify)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
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
