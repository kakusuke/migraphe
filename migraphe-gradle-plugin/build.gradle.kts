plugins {
    `java-gradle-plugin`
}

val generatorJsonJar by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val jdbcPluginJar by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":migraphe-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
    testImplementation(libs.h2)

    generatorJsonJar(project(":migraphe-plugin-generator-json"))

    jdbcPluginJar(project(":migraphe-plugin-jdbc"))
    jdbcPluginJar(libs.h2)
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
    val jdbcPluginFiles = configurations["jdbcPluginJar"]
    inputs.files(jdbcPluginFiles).withPropertyName("jdbcPluginClasspath")
    doFirst {
        systemProperty(
            "generator.json.classpath",
            generatorJsonFiles.files.joinToString(File.pathSeparator) { it.absolutePath },
        )
        systemProperty(
            "jdbc.plugin.classpath",
            jdbcPluginFiles.files.joinToString(File.pathSeparator) { it.absolutePath },
        )
    }
}
