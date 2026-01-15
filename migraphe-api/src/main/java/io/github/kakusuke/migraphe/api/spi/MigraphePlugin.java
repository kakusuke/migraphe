package io.github.kakusuke.migraphe.api.spi;

/**
 * Migraphe プラグインの統合インターフェース。
 *
 * <p>プラグインは ServiceLoader で発見され、type によって識別される。 各プラグインは Environment, MigrationNode,
 * HistoryRepository の生成を担当する。
 *
 * <p>実装例:
 *
 * <pre>{@code
 * public class PostgreSQLPlugin implements MigraphePlugin<String> {
 *     @Override
 *     public String type() {
 *         return "postgresql";
 *     }
 *
 *     @Override
 *     public Class<SqlTaskDefinition> taskDefinitionClass() {
 *         return SqlTaskDefinition.class;
 *     }
 *
 *     @Override
 *     public Class<PostgreSQLEnvironmentDefinition> environmentDefinitionClass() {
 *         return PostgreSQLEnvironmentDefinition.class;
 *     }
 *
 *     @Override
 *     public EnvironmentProvider environmentProvider() {
 *         return new PostgreSQLEnvironmentProvider();
 *     }
 *
 *     @Override
 *     public MigrationNodeProvider<String> migrationNodeProvider() {
 *         return new PostgreSQLMigrationNodeProvider();
 *     }
 *
 *     @Override
 *     public HistoryRepositoryProvider historyRepositoryProvider() {
 *         return new PostgreSQLHistoryRepositoryProvider();
 *     }
 * }
 * }</pre>
 *
 * @param <T> TaskDefinition の UP/DOWN アクション型（例: PostgreSQL では String）
 */
public interface MigraphePlugin<T> {

    /**
     * プラグインの型識別子を返す。
     *
     * <p>設定ファイルで使用される型名（例: "postgresql", "mysql", "mongodb"）
     *
     * @return プラグインの型識別子
     */
    String type();

    /**
     * プラグイン固有の TaskDefinition サブタイプを返す。
     *
     * <p>フレームワークは返されたクラスを使用して YAML から TaskDefinition をマッピングする。 サブタイプは {@code @ConfigMapping}
     * アノテーション付きで実装する必要がある。
     *
     * @return TaskDefinition サブタイプの Class
     */
    Class<? extends TaskDefinition<T>> taskDefinitionClass();

    /**
     * プラグイン固有の EnvironmentDefinition サブタイプを返す。
     *
     * <p>フレームワークは返されたクラスを使用して YAML から EnvironmentDefinition をマッピングする。 サブタイプは {@code @ConfigMapping}
     * アノテーション付きで実装する必要がある。
     *
     * @return EnvironmentDefinition サブタイプの Class
     */
    Class<? extends EnvironmentDefinition> environmentDefinitionClass();

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
    MigrationNodeProvider<T> migrationNodeProvider();

    /**
     * HistoryRepository を生成する Provider を返す。
     *
     * @return HistoryRepositoryProvider
     */
    HistoryRepositoryProvider historyRepositoryProvider();
}
