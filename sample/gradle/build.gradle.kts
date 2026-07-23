plugins {
    id("io.github.kakusuke.migraphe") version "v0.4.3"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.4.3")
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.4.3")
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.4.3")
}
