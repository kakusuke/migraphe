plugins {
    `java-library`
}

dependencies {
    // API dependency - compileOnly because host application provides it at runtime
    compileOnly(project(":migraphe-api"))
    // JDBC common module
    api(project(":migraphe-plugin-jdbc"))
    // API needed for tests
    testImplementation(project(":migraphe-api"))

    // MySQL JDBC
    implementation(libs.mysql.connector.j)

    // MicroProfile Config (for @ConfigMapping)
    implementation(libs.smallrye.config)

    // Null safety
    compileOnly(libs.jspecify)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.smallrye.config.source.yaml)

    // Testcontainers
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.mysql)
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

// Fat JAR task (CLI plugin - includes JDBC driver)
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
