plugins {
    id("io.github.kakusuke.migraphe") version "main-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:main-SNAPSHOT")
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:main-SNAPSHOT")
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-generator-json:main-SNAPSHOT")
}
