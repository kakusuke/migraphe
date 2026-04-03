package io.github.kakusuke.migraphe.cli.resolver;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** migraphe.yaml の plugins: セクションを読み取り、URLClassLoader を構築する。 */
public final class PluginResolver {

    private final PluginConfigPreParser preParser;
    private final MavenPluginResolver mavenResolver;

    public PluginResolver() {
        this.preParser = new PluginConfigPreParser();
        this.mavenResolver = new MavenPluginResolver();
    }

    public @Nullable URLClassLoader resolve(Path baseDir) {
        List<MavenArtifactCoordinate> coords =
                preParser.parsePlugins(baseDir.resolve("migraphe.yaml"));
        if (coords.isEmpty()) {
            return null;
        }
        List<Path> jars = mavenResolver.resolveAll(coords);
        URL[] urls =
                jars.stream()
                        .map(
                                p -> {
                                    try {
                                        return p.toUri().toURL();
                                    } catch (MalformedURLException e) {
                                        throw new IllegalStateException(e);
                                    }
                                })
                        .toArray(URL[]::new);
        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }
}
