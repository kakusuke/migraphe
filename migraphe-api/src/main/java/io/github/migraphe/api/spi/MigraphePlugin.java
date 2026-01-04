package io.github.migraphe.api.spi;

/**
 * Migraphe プラグインの統合インターフェース。
 *
 * <p>プラグインは ServiceLoader で発見され、type によって識別される。 各プラグインは Environment, MigrationNode,
 * HistoryRepository の生成を担当する。
 *
 * <p>実装例:
 *
 * <pre>{@code
 * public class PostgreSQLPlugin implements MigraphePlugin {
 *     @Override
 *     public String type() {
 *         return "postgresql";
 *     }
 *
 *     @Override
 *     public EnvironmentProvider environmentProvider() {
 *         return new PostgreSQLEnvironmentProvider();
 *     }
 *
 *     @Override
 *     public MigrationNodeProvider migrationNodeProvider() {
 *         return new PostgreSQLMigrationNodeProvider();
 *     }
 *
 *     @Override
 *     public HistoryRepositoryProvider historyRepositoryProvider() {
 *         return new PostgreSQLHistoryRepositoryProvider();
 *     }
 * }
 * }</pre>
 */
public interface MigraphePlugin {

    /**
     * プラグインの型識別子を返す。
     *
     * <p>設定ファイルで使用される型名（例: "postgresql", "mysql", "mongodb"）
     *
     * @return プラグインの型識別子
     */
    String type();

    /**
     * Environment を生成する Provider を返す。
     *
     * @return EnvironmentProvider
     */
    EnvironmentProvider environmentProvider();

    /**
     * MigrationNode を生成する Provider を返す。
     *
     * @return MigrationNodeProvider
     */
    MigrationNodeProvider migrationNodeProvider();

    /**
     * HistoryRepository を生成する Provider を返す。
     *
     * @return HistoryRepositoryProvider
     */
    HistoryRepositoryProvider historyRepositoryProvider();
}
