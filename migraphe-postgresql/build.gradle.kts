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

// Fat JAR タスク（CLI プラグイン用 - JDBC ドライバ込み）
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    with(tasks.jar.get() as CopySpec)
}
