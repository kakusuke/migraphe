plugins {
    `java-library`
    application
}

dependencies {
    // コア
    api(project(":migraphe-core"))

    // プラグイン（テスト用のみ - 本番は plugins/ ディレクトリから動的ロード）
    testImplementation(project(":migraphe-plugin-postgresql"))

    // CLI フレームワーク
    implementation(libs.picocli)

    // Maven Resolver（プラグイン依存解決）
    implementation(libs.maven.resolver.provider)
    implementation(libs.maven.resolver.connector.basic)
    implementation(libs.maven.resolver.transport.file)
    implementation(libs.maven.resolver.transport.http)

    // SLF4J NOP（Maven Resolver の SLF4J 警告を抑制）
    implementation(libs.slf4j.nop)

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
    mainClass.set("io.github.kakusuke.migraphe.cli.Main")
    applicationName = "migraphe"
}

tasks.distTar {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
    archiveFileName.set("migraphe-${project.version}.tar.gz")
    eachFile { path = path.replaceFirst(Regex("^migraphe-[^/]+"), "migraphe") }
    includeEmptyDirs = false
}

tasks.distZip {
    archiveFileName.set("migraphe-${project.version}.zip")
    eachFile { path = path.replaceFirst(Regex("^migraphe-[^/]+"), "migraphe") }
    includeEmptyDirs = false
}

tasks.processResources {
    val gitCommit = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.orElse("unknown")

    inputs.property("version", project.version.toString())
    inputs.property("commit", gitCommit)

    filesMatching("migraphe-version.properties") {
        expand(
            "version" to project.version.toString(),
            "commit" to gitCommit.get()
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

// Fat JAR タスク（配布用）
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("migraphe")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(tasks.jar)
    dependsOn(configurations.runtimeClasspath)

    manifest {
        attributes["Main-Class"] = "io.github.kakusuke.migraphe.cli.Main"
    }

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    with(tasks.jar.get() as CopySpec)
}
