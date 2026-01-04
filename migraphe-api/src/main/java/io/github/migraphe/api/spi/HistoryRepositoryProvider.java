package io.github.migraphe.api.spi;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.history.HistoryRepository;

/**
 * HistoryRepository を生成する Provider インターフェース。
 *
 * <p>プラグインは Environment から HistoryRepository インスタンスを生成する責任を持つ。
 */
public interface HistoryRepositoryProvider {

    /**
     * Environment から HistoryRepository を生成する。
     *
     * @param environment 履歴を保存する環境
     * @return HistoryRepository インスタンス
     */
    HistoryRepository createRepository(Environment environment);
}
