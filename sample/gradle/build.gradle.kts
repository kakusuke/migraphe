plugins {
    id("io.github.kakusuke.migraphe") version "0.1.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    migraphePlugin("io.github.kakusuke.migraphe:migraphe-plugin-postgresql:0.1.0-SNAPSHOT")
    migraphePlugin("io.github.kakusuke.migraphe:migraphe-plugin-mysql:0.1.0-SNAPSHOT")
    migraphePlugin("io.github.kakusuke.migraphe:migraphe-plugin-generator-json:0.1.0-SNAPSHOT")
}
