# Migraphe プラグイン開発ガイド

Migraphe のカスタムプラグイン作成方法を説明します。

## 概要

Migraphe は Java の ServiceLoader メカニズムに基づいたプラグインシステムを使用しています。プラグインは以下を提供できます：
- **Environment** - データベース接続管理
- **MigrationNode** - マイグレーションタスク定義
- **HistoryRepository** - 実行履歴の永続化

## クイックスタート

### 1. 依存関係の追加

プラグインプロジェクトに `migraphe-api` モジュールを追加します。Migraphe のアーティファクトは現在 JitPack 経由で配布されています：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.kakusuke.migraphe:migraphe-api:v0.6.0")
}
```

### 2. MigraphePlugin の実装

`MigraphePlugin` を実装するクラスを作成します：

```java
package com.example.myplugin;

import io.github.kakusuke.migraphe.api.spi.*;

public class MyDatabasePlugin implements MigraphePlugin {

    @Override
    public String type() {
        return "mydatabase";  // target.*.type 設定で使用
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new MyDatabaseEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider migrationNodeProvider() {
        return new MyDatabaseMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new MyDatabaseHistoryRepositoryProvider();
    }
}
```

### 3. Provider の実装

#### EnvironmentProvider

```java
public class MyDatabaseEnvironmentProvider implements EnvironmentProvider {

    @Override
    public Environment createEnvironment(String name, EnvironmentConfig config) {
        String connectionString = config.get("connection_string")
            .orElseThrow(() -> new IllegalArgumentException("connection_string は必須です"));

        return new MyDatabaseEnvironment(name, connectionString);
    }
}
```

#### MigrationNodeProvider

フレームワークが依存関係を解決します。Provider は以下を受け取ります：
- `TaskDefinition` - 型安全なタスク設定（name, up SQL, down SQL）
- `Set<NodeId>` - フレームワークが解決済みの依存関係

```java
public class MyDatabaseMigrationNodeProvider implements MigrationNodeProvider {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition task,
            Set<NodeId> dependencies,
            Environment environment) {

        // TaskDefinition から SQL を取得
        String upSql = task.up().sql()
            .orElseThrow(() -> new IllegalArgumentException("up.sql は必須です"));

        String downSql = task.down()
            .flatMap(SqlDefinition::sql)
            .orElse(null);

        return new MyDatabaseMigrationNode(
            nodeId,
            task.name(),
            task.description().orElse(""),
            environment,
            dependencies,  // フレームワークから提供
            upSql,
            downSql
        );
    }
}
```

#### HistoryRepositoryProvider

```java
public class MyDatabaseHistoryRepositoryProvider implements HistoryRepositoryProvider {

    @Override
    public HistoryRepository createRepository(Environment environment) {
        return new MyDatabaseHistoryRepository((MyDatabaseEnvironment) environment);
    }
}
```

### 4. ServiceLoader の登録

サービス登録ファイルを作成します：

```
src/main/resources/META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin
```

内容：
```
com.example.myplugin.MyDatabasePlugin
```

### 5. プラグイン JAR のビルド

```bash
./gradlew jar
```

## プラグインの使用方法

Migraphe はクラスパスと、プロジェクトが宣言した Maven 座標からプラグインを読み込みます。JAR を
ディレクトリから探索することはないため、リゾルバが到達できる場所に publish する必要があります。開発中は
ローカルの `~/.m2` への install で十分です。

### 方法 1: Maven 座標（CLI）

JAR を publish し（開発中は `./gradlew publishToMavenLocal`）、`migraphe.yaml` に宣言して pin します：

```yaml
plugins:
  - coordinate: com.example:my-database-plugin:1.0.0
```

```bash
migraphe pin
```

`plugins:` を宣言した場合はロックファイルが必須です。`migraphe pin` が生成します。

### 方法 2: migraphePlugin コンフィギュレーション（Gradle）

```kotlin
dependencies {
    migraphePlugin("com.example:my-database-plugin:1.0.0")
}
```

### 方法 3: クラスパス

Migraphe 実行時にプラグインをクラスパスに追加します。

## 設定

ターゲット YAML ファイルでプラグインを設定します：

```yaml
# targets/mydb.yaml
type: mydatabase
connection_string: "mydb://localhost:1234/database"
username: user
password: secret
```

## SPI インターフェースリファレンス

### MigraphePlugin

```java
public interface MigraphePlugin {
    String type();
    EnvironmentProvider environmentProvider();
    MigrationNodeProvider migrationNodeProvider();
    HistoryRepositoryProvider historyRepositoryProvider();
}
```

### EnvironmentProvider

```java
public interface EnvironmentProvider {
    Environment createEnvironment(String name, EnvironmentConfig config);
}
```

### MigrationNodeProvider

```java
public interface MigrationNodeProvider {
    MigrationNode createNode(
        NodeId nodeId,
        TaskDefinition task,
        Set<NodeId> dependencies,
        Environment environment);
}
```

### HistoryRepositoryProvider

```java
public interface HistoryRepositoryProvider {
    HistoryRepository createRepository(Environment environment);
}
```

### TaskDefinition

フレームワークが提供する型安全なタスク設定：

```java
public interface TaskDefinition {
    String name();
    Optional<String> description();
    SqlDefinition up();
    Optional<SqlDefinition> down();
}
```

### SqlDefinition

```java
public interface SqlDefinition {
    Optional<String> sql();
    Optional<String> file();
    Optional<String> resource();
}
```

## 実装が必要なコアインターフェース

### Environment

```java
public interface Environment {
    EnvironmentId id();
    String name();
}
```

### MigrationNode

```java
public interface MigrationNode {
    NodeId id();
    String name();
    Environment environment();
    Set<NodeId> dependencies();
    Task upTask();
    Optional<Task> downTask();
}
```

### Task

```java
public interface Task {
    Result<TaskResult, String> execute();
}
```

### HistoryRepository

```java
public interface HistoryRepository {
    void initialize();
    void record(ExecutionRecord record);
    boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId);
    List<NodeId> executedNodes(EnvironmentId environmentId);
    List<ExecutionRecord> allRecords(EnvironmentId environmentId);
    Optional<ExecutionRecord> findLatestRecord(NodeId nodeId, EnvironmentId environmentId);
}
```

## 例: PostgreSQL プラグイン

完全な実装例として `migraphe-plugin-postgresql` モジュールを参照してください：
- `PostgreSQLPlugin` - メインプラグインクラス
- `PostgreSQLEnvironmentProvider` - PostgreSQLEnvironment を生成
- `PostgreSQLMigrationNodeProvider` - PostgreSQLMigrationNode を生成
- `PostgreSQLHistoryRepositoryProvider` - PostgreSQLHistoryRepository を生成

## ベストプラクティス

1. **軽量な API 依存**: `migraphe-core` ではなく `migraphe-api` のみに依存する
2. **明確なエラーメッセージ**: 設定エラーには説明的な例外をスロー
3. **トランザクション管理**: Task 実装でトランザクションを適切に処理
4. **テスト**: 実際のデータベースインスタンスで統合テスト（例: Testcontainers）
5. **ServiceLoader 登録**: META-INF/services ファイルを忘れずに
6. **依存関係の処理**: 依存関係はフレームワークに任せ、タスク実行に集中する
