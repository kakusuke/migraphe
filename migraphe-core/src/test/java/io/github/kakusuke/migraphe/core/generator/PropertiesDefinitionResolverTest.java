package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithDefault;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PropertiesDefinitionResolverTest {

    @ConfigMapping(prefix = "")
    public interface SampleDefinition extends GeneratorDefinition {
        @Override
        String type();

        String name();

        @WithDefault("default-out")
        String outputDir();

        Optional<List<ExcludePattern>> excludes();

        interface ExcludePattern {
            Optional<String> schema();

            Optional<String> table();
        }
    }

    @Test
    void resolveMapsPrefixedPropertiesToTypedDefinition() {
        SmallRyeConfig source =
                buildConfig(
                        Map.of(
                                "generators[0].type", "sample",
                                "generators[0].name", "mydb",
                                "generators[0].output-dir", "docs/out",
                                "generators[0].excludes[0].schema", "internal",
                                "generators[1].type", "other",
                                "generators[1].name", "otherdb"));

        var resolver = new PropertiesDefinitionResolver(source, "generators[0]");

        SampleDefinition definition = resolver.resolve(SampleDefinition.class);

        assertThat(definition.type()).isEqualTo("sample");
        assertThat(definition.name()).isEqualTo("mydb");
        assertThat(definition.outputDir()).isEqualTo("docs/out");
        assertThat(definition.excludes()).isPresent();
        assertThat(definition.excludes().get()).hasSize(1);
        assertThat(definition.excludes().get().get(0).schema()).contains("internal");
    }

    @Test
    void resolveReturnsProxyLoadedFromRequestedClassLoader() throws Exception {
        SmallRyeConfig source =
                buildConfig(
                        Map.of(
                                "generators[0].type", "sample",
                                "generators[0].name", "mydb"));
        var resolver = new PropertiesDefinitionResolver(source, "generators[0]");

        // 別クラスローダーで SampleDefinition を再定義する
        Class<?> isolatedClass = loadClassInIsolation(SampleDefinition.class);
        assertThat(isolatedClass.getClassLoader())
                .isNotSameAs(SampleDefinition.class.getClassLoader());

        @SuppressWarnings("unchecked")
        Class<? extends GeneratorDefinition> cast =
                (Class<? extends GeneratorDefinition>) isolatedClass;
        GeneratorDefinition instance = resolver.resolve(cast);

        // 生成された proxy は渡したクラス（= 別 CL 側の SampleDefinition）を実装しているため、
        // その CL 側の Class.cast でも ClassCastException が発生しない。
        assertThat(isolatedClass.isInstance(instance)).isTrue();
    }

    private static SmallRyeConfig buildConfig(Map<String, String> props) {
        return (SmallRyeConfig)
                new SmallRyeConfigBuilder()
                        .withSources(
                                new io.github.kakusuke.migraphe.core.config.MapConfigSource(
                                        new HashMap<>(props)))
                        .build();
    }

    private static Class<?> loadClassInIsolation(Class<?> original) throws IOException {
        ClassLoader parent = original.getClassLoader();
        String resource = original.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = parent.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resource);
            }
            bytes = in.readAllBytes();
        }
        ClassLoader isolated =
                new ClassLoader(parent) {
                    @Override
                    public Class<?> loadClass(String name, boolean resolve)
                            throws ClassNotFoundException {
                        if (name.equals(original.getName())) {
                            Class<?> loaded = findLoadedClass(name);
                            if (loaded == null) {
                                loaded = defineClass(name, bytes, 0, bytes.length);
                            }
                            if (resolve) {
                                resolveClass(loaded);
                            }
                            return loaded;
                        }
                        return super.loadClass(name, resolve);
                    }
                };
        try {
            return isolated.loadClass(original.getName());
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }
}
