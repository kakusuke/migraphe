package io.github.kakusuke.migraphe.cli.config;

import io.smallrye.config.SmallRyeConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;

/**
 * 指定されたプレフィックスを持つプロパティを、プレフィックスを除去してエクスポーズする ConfigSource。
 *
 * <p>例: prefix="target.db1." の場合
 *
 * <ul>
 *   <li>"target.db1.type" → "type"
 *   <li>"target.db1.jdbc_url" → "jdbc_url"
 * </ul>
 */
public class PrefixedConfigSource implements ConfigSource {

    private final Map<String, String> properties;
    private final String name;

    /**
     * コンストラクタ。
     *
     * @param sourceConfig 元の設定
     * @param prefix プレフィックス（末尾に "." を含むこと）
     */
    public PrefixedConfigSource(SmallRyeConfig sourceConfig, String prefix) {
        this.name = "PrefixedConfigSource[" + prefix + "]";
        this.properties = new HashMap<>();

        for (String propertyName : sourceConfig.getPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                String strippedName = propertyName.substring(prefix.length());
                String value = sourceConfig.getValue(propertyName, String.class);
                if (value != null) {
                    properties.put(strippedName, value);
                }
            }
        }
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
        return name;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.copyOf(properties);
    }
}
