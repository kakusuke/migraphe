package io.github.kakusuke.migraphe.core.config;

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;

/**
 * Map をラップする ConfigSource。
 *
 * <p>Gradle DSL の variables など外部から注入された変数を SmallRye Config に差し込むために使用する。 ordinal 600
 * により、環境ファイル（500）や YAML 設定（100）より高い優先度を持つ。
 */
public class MapConfigSource implements ConfigSource {

    private static final String NAME = "MapConfigSource";
    private static final int ORDINAL = 600;

    private final Map<String, String> properties;

    /**
     * コンストラクタ。
     *
     * @param properties 変数マップ
     */
    public MapConfigSource(Map<String, String> properties) {
        this.properties = Map.copyOf(properties);
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public @Nullable String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }
}
