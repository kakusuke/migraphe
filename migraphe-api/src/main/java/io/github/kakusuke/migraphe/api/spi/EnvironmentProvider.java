package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.environment.Environment;

/**
 * Environment を生成する Provider インターフェース。
 *
 * <p>プラグインは設定から Environment インスタンスを生成する責任を持つ。
 */
public interface EnvironmentProvider {

    /**
     * 環境定義から Environment を生成する。
     *
     * @param name 環境名（target ID）
     * @param definition 環境定義（プラグイン固有の設定）
     * @return Environment インスタンス
     */
    Environment createEnvironment(String name, EnvironmentDefinition definition);
}
