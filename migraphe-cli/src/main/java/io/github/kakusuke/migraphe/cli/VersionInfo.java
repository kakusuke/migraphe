package io.github.kakusuke.migraphe.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

public record VersionInfo(String version, String commit) {

    public String format() {
        return "migraphe " + version + " (" + commit + ")";
    }

    public static VersionInfo load(ClassLoader classLoader) {
        try (InputStream stream = classLoader.getResourceAsStream("migraphe-version.properties")) {
            if (stream == null) {
                return new VersionInfo("unknown", "unknown");
            }
            Properties props = new Properties();
            props.load(stream);
            return new VersionInfo(
                    props.getProperty("version", "unknown"),
                    props.getProperty("commit", "unknown"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
