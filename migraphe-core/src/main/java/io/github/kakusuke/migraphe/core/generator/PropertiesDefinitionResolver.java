package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.generator.DefinitionResolver;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.core.config.MapConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * SmallRye Config の property snapshot を受け取り、プラグインのクラスローダー上で GeneratorDefinition
 * サブタイプの @ConfigMapping proxy を再具現化する DefinitionResolver。
 *
 * <p>{@link SmallRyeConfigBuilder#withMapping(Class)} が受け取った Class の classloader を proxy 生成に用いるため、
 * 異なるクラスローダー間でも ClassCastException を起こさずに typed definition を受け渡しできる。
 */
public final class PropertiesDefinitionResolver implements DefinitionResolver {

    private final Map<String, String> properties;

    /**
     * SmallRyeConfig から指定 prefix 配下のプロパティをスナップショットして構築する。
     *
     * @param source 元の SmallRyeConfig
     * @param prefix プロパティ prefix（例: "generators[0]"）。末尾のドットは不要。
     */
    public PropertiesDefinitionResolver(SmallRyeConfig source, String prefix) {
        this.properties = extractProperties(source, prefix);
    }

    private static Map<String, String> extractProperties(SmallRyeConfig source, String prefix) {
        String prefixDot = prefix + ".";
        Map<String, String> snapshot = new HashMap<>();
        for (String key : source.getPropertyNames()) {
            if (key.startsWith(prefixDot)) {
                String stripped = key.substring(prefixDot.length());
                try {
                    String value = source.getRawValue(key);
                    if (value != null) {
                        snapshot.put(stripped, value);
                    }
                } catch (NoSuchElementException ignored) {
                    // skip
                }
            }
        }
        return snapshot;
    }

    @Override
    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(new MapConfigSource(properties))
                        .withMapping(klass)
                        .withValidateUnknown(false)
                        .build();
        return config.getConfigMapping(klass);
    }
}
