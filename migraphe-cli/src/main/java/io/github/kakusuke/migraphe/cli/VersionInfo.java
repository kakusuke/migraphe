package io.github.kakusuke.migraphe.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Build-time version metadata for the Migraphe CLI.
 *
 * <p>The values are populated from a {@code migraphe-version.properties} resource generated during
 * the build and bundled on the classpath. They back the {@code -v}/{@code --version} command in
 * {@link Main}.
 *
 * @param version the release version string (e.g. {@code "0.4.2"}), or {@code "unknown"} if the
 *     version resource is absent or omits the property
 * @param commit the source-control commit identifier the artifact was built from, or {@code
 *     "unknown"} if the version resource is absent or omits the property
 */
public record VersionInfo(String version, String commit) {

    /**
     * Formats the version metadata for display on the console.
     *
     * @return a human-readable string of the form {@code "migraphe <version> (<commit>)"}
     */
    public String format() {
        return "migraphe " + version + " (" + commit + ")";
    }

    /**
     * Loads version metadata from the {@code migraphe-version.properties} classpath resource.
     *
     * <p>If the resource cannot be found, a {@code VersionInfo} with both fields set to {@code
     * "unknown"} is returned; missing individual properties likewise default to {@code "unknown"}.
     *
     * @param classLoader the class loader used to locate the version properties resource
     * @return the loaded version metadata, never {@code null}
     * @throws java.io.UncheckedIOException if reading the properties resource fails
     */
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
