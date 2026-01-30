plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":migraphe-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("migraphe") {
            id = "io.github.kakusuke.migraphe"
            implementationClass = "io.github.kakusuke.migraphe.gradle.MigrapheGradlePlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
