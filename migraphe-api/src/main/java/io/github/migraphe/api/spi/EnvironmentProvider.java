package io.github.migraphe.api.spi;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.environment.EnvironmentConfig;

/**
 * Environment を生成する Provider インターフェース。
 *
 * <p>プラグインは設定から Environment インスタンスを生成する責任を持つ。
 */
public interface EnvironmentProvider {

    /**
     * 設定から Environment を生成する。
     *
     * @param name 環境名（target ID）
     * @param config 環境設定
     * @return Environment インスタンス
     */
    Environment createEnvironment(String name, EnvironmentConfig config);
}
