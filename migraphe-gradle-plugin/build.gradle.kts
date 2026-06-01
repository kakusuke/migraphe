plugins {
    `java-gradle-plugin`
}

val generatorJsonJar by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":migraphe-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())

    generatorJsonJar(project(":migraphe-plugin-generator-json"))
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
    val generatorJsonFiles = configurations["generatorJsonJar"]
    inputs.files(generatorJsonFiles).withPropertyName("generatorJsonClasspath")
    doFirst {
        systemProperty(
            "generator.json.classpath",
            generatorJsonFiles.files.joinToString(File.pathSeparator) { it.absolutePath },
        )
    }
}
