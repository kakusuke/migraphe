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

// Fat JAR task (CLI plugin - includes Jackson dependencies)
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(tasks.jar)
    dependsOn(configurations.runtimeClasspath)

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    with(tasks.jar.get() as CopySpec)
}
