import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java")
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
}

allprojects {
    // デフォルト: io.github.kakusuke.migraphe (Maven Central 互換、ローカル開発用)
    // JitPack ビルド時のみ -PpublishGroup=com.github.kakusuke.migraphe で上書き
    group = providers.gradleProperty("publishGroup").getOrElse("io.github.kakusuke.migraphe")
    // version は gradle.properties で管理

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.ltgt.errorprone")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "errorprone"(rootProject.libs.errorprone.core)
        "errorprone"(rootProject.libs.nullaway)
        "compileOnly"(rootProject.libs.jspecify)
        "testCompileOnly"(rootProject.libs.jspecify)
        // Gradle 9 no longer adds the JUnit Platform launcher to the test
        // runtime classpath implicitly; declare it for all subprojects.
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp().reflowLongStrings()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            // Enable NullAway only for main source code, not test code
            if (name == "compileJava") {
                error("NullAway")
            } else {
                disable("NullAway")
            }
            option("NullAway:AnnotatedPackages", "io.github.kakusuke.migraphe")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // maven-publish: cli 以外のモジュールを publishToMavenLocal 可能にする
    if (name != "migraphe-cli") {
        apply(plugin = "maven-publish")

        afterEvaluate {
            configure<PublishingExtension> {
                publications {
                    // java-gradle-plugin は自動で pluginMaven publication を生成するのでスキップ
                    if (plugins.hasPlugin("java-gradle-plugin").not()) {
                        create<MavenPublication>("maven") {
                            from(components["java"])
                        }
                    }
                }
            }
        }
    }
}
