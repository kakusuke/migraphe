package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import org.gradle.api.logging.Logger;

/** HistoryRepository の取得を共通化するヘルパー。 */
public final class HistoryRepositoryHelper {

    private HistoryRepositoryHelper() {}

    /**
     * ExecutionContext から HistoryRepository を取得する。
     *
     * @param context 実行コンテキスト
     * @param logger Gradle ロガー
     * @return HistoryRepository
     */
    public static HistoryRepository getHistoryRepository(ExecutionContext context, Logger logger) {
        String historyTarget =
                context.config().getConfigMapping(ProjectConfig.class).history().target();

        Environment historyEnv = context.environments().get(historyTarget);

        if (historyEnv == null) {
            logger.warn(
                    "Warning: History target '{}' not found. Using in-memory history repository.",
                    historyTarget);
            return new InMemoryHistoryRepository();
        }

        String type = context.config().getValue("target." + historyTarget + ".type", String.class);

        MigraphePlugin<?> plugin = context.pluginRegistry().getRequiredPlugin(type);

        return plugin.historyRepositoryProvider().createRepository(historyEnv);
    }
}
