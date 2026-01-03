plugins {
    `java-library`
    application
}

dependencies {
    // コアとプラグイン
    api(project(":migraphe-core"))
    implementation(project(":migraphe-postgresql"))

    // CLI フレームワーク
    implementation(libs.picocli)

    // TOML パース
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)

    // テスト
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)

    // Testcontainers（統合テスト用）
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

application {
    mainClass.set("io.github.migraphe.cli.Main")
}

tasks.test {
    useJUnitPlatform()
}

// Fat JAR タスク（配布用）
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.github.migraphe.cli.Main"
    }

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    with(tasks.jar.get() as CopySpec)
}
