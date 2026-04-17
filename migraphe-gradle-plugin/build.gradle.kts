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
    testRuntimeOnly(libs.junit.platform.launcher)
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
    doFirst {
        val classpath = configurations["generatorJsonJar"].files
            .joinToString(File.pathSeparator) { it.absolutePath }
        systemProperty("generator.json.classpath", classpath)
    }
}
