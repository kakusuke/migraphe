# Migraphe プラグイン開発ガイド

Migraphe のカスタムプラグイン作成方法を説明します。

## 概要

Migraphe は Java の ServiceLoader メカニズムに基づいたプラグインシステムを使用しています。プラグインは以下を提供できます：
- **Target** - データベース接続管理
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
    implementation("com.github.kakusuke.migraphe:migraphe-api:v0.7.0")
}
```

coordinate は実際に動かす migraphe と揃える必要があります。API は 0.7.0 で壊れており
（[0.7.0 での変更](#070-での変更)）、古いバージョン向けにビルドされた jar は読み込まれたうえで最初の
呼び出しで `AbstractMethodError` になります。

### 2. MigraphePlugin の実装

`MigraphePlugin` を実装するクラスを作成します：

```java
package com.example.myplugin;

import io.github.kakusuke.migraphe.api.spi.*;

public class MyDatabasePlugin implements MigraphePlugin<String> {

    @Override
    public String type() {
        return "mydatabase";  // target.*.type 設定で使用
    }

    @Override
    public TargetProvider targetProvider() {
        return new MyDatabaseTargetProvider();
    }

    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new MyDatabaseMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new MyDatabaseHistoryRepositoryProvider();
    }
}
```

### 3. Provider の実装

#### TargetProvider

`TargetDefinition` が持つのは `type()` だけです。`targets/*.yaml` の他のキーはサブインターフェースに置き、
対応付けは SmallRye に任せます——名前で引くのではなく、型付きのアクセサとして読みます：

```java
public interface MyTargetDefinition extends TargetDefinition {
    String connectionString();
    Optional<String> password();
}

public class MyDatabaseTargetProvider implements TargetProvider {

    @Override
    public Target createTarget(String name, TargetDefinition definition) {
        if (!(definition instanceof MyTargetDefinition mine)) {
            throw new IllegalArgumentException(
                "MyTargetDefinition を期待しましたが " + definition.getClass().getName() + " でした");
        }

        return new MyDatabaseTarget(name, mine.connectionString(), mine.password().orElse(null));
    }
}
```

#### MigrationNodeProvider

フレームワークが依存関係を解決します。Provider は以下を受け取ります：
- `TaskDefinition<T>` - 型安全なタスク設定。自前のサブインターフェースにキャストすると、そのプラグイン
  だけが解釈するキーを読めます
- `Set<NodeId>` - フレームワークが解決済みの依存関係

```java
public class MyDatabaseMigrationNodeProvider implements MigrationNodeProvider<String> {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<String> task,
            Set<NodeId> dependencies,
            Target target) {

        String upSql = task.up();
        String downSql = task.down().filter(sql -> !sql.isBlank()).orElse(null);

        // タスクはロールバックか、それが無い理由かのどちらかを宣言します。自前の定義インターフェースへ
        // キャストするのが、そのプラグイン固有のキーを読む方法です。
        String noWayBack =
            task instanceof MyTaskDefinition mine ? mine.noWayBack().orElse(null) : null;

        return new MyDatabaseMigrationNode(
            nodeId,
            task.name(),
            task.description().orElse(null),
            target,
            dependencies,  // フレームワークから提供
            upSql,
            downSql,
            noWayBack
        );
    }
}
```

#### HistoryRepositoryProvider

```java
public class MyDatabaseHistoryRepositoryProvider implements HistoryRepositoryProvider {

    @Override
    public HistoryRepository createRepository(Target target) {
        return new MyDatabaseHistoryRepository((MyDatabaseTarget) target);
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
public interface MigraphePlugin<T> {
    String type();
    TargetProvider targetProvider();
    MigrationNodeProvider<T> migrationNodeProvider();
    HistoryRepositoryProvider historyRepositoryProvider();
}
```

`T` は `up:` / `down:` キーが写る型です。タスクが SQL であるプラグインなら `String`。

### TargetProvider

```java
public interface TargetProvider {
    Target createTarget(String name, TargetDefinition config);
}
```

### MigrationNodeProvider

```java
public interface MigrationNodeProvider<T> {
    MigrationNode createNode(
        NodeId nodeId,
        TaskDefinition<T> task,
        Set<NodeId> dependencies,
        Target target);
}
```

### HistoryRepositoryProvider

```java
public interface HistoryRepositoryProvider {
    HistoryRepository createRepository(Target target);
}
```

### TaskDefinition

フレームワークが提供する型安全なタスク設定です。`T` はプラグインの `up:` / `down:` キーが保持するもの
——JDBC 系なら素の SQL ですが、レコード型に写しても構いません：

```java
public interface TaskDefinition<T> {
    String name();
    Optional<String> description();
    String target();
    Optional<List<String>> dependencies();
    T up();
    Optional<T> down();
}
```

自前の定義インターフェースでこれを継承し、そのタスクの YAML が持つ他のキーを足します。キーの対応付けは
SmallRye が行うので、YAML 側の綴りが違う場合は `@WithName` で名前を与えます：

```java
public interface SqlTaskDefinition extends TaskDefinition<String> {
    Optional<Boolean> autocommit();

    @WithName("autocommit.up")
    Optional<Boolean> autocommitUp();
}
```

## 実装が必要なコアインターフェース

### Target

```java
public interface Target {
    TargetId id();
    String name();
}
```

target は**接続**であり、それだけを意味します。（0.7.0 より前は `Environment` という名前でした。
いまや *environment* は `environments/*.yaml` のオーバーレイだけを指します。）

### MigrationNode

```java
public interface MigrationNode {
    NodeId id();
    String name();
    @Nullable String description();
    Target target();
    Set<NodeId> dependencies();
    Task upTask();
    @Nullable Task downTask();

    @Nullable String fingerprint(Fingerprinter fingerprinter);

    default @Nullable String noWayBack() {
        return null;
    }
}
```

**`fingerprint` にデフォルト実装は無く、必ず実装します。** これは「定義と一致したままのマイグレーション」と
「適用後に編集されたマイグレーション」を `status` が見分けるための値で、答えられないプラグインは
`status` 以外のすべてのコマンドを止めます。自分の持ち分を `Fingerprinter` に渡し、返ってきたものを返して
ください——枠付け・依存閉包の連結・ハッシュ化はコアが行うので、どのプラグインも同じ畳み方になります：

```java
@Override
public @Nullable String fingerprint(Fingerprinter fingerprinter) {
    return downTask == null
            ? fingerprinter.over(upTask.signature())
            : fingerprinter.over(upTask.signature(), downTask.signature());
}
```

`null` を返すことは**辞退の手段ではありません**。設計が残している唯一の null は、履歴の行を包むコア自身の
アダプタが「その行にトークンが無い」と報告するためのものです。宣言されたノードが null を返すのは契約違反で、
プラグインの不具合として報告されます。

`noWayBack()` は「そのマイグレーションが一方向である理由」を返し、そうでなければ `null` です。
`downTask()` が無いだけでは答えられない問い——著者が決めたのか、書き忘れたのか——に答えます。両者は
正反対の対応を要求するので、タスクはどちらかを必ず宣言します。すべてのタスクが宣言するまで `up` は
プロジェクト全体を拒否します。

### Task

```java
public interface Task {
    Result<TaskResult, String> execute();
    String description();
    String signature();
}
```

**`signature` にもデフォルトはありません。** fingerprint のうちプラグインの持ち分を畳む元になるテキストで、
そのタスクの振る舞いを変えうるものをすべて含める必要があります。複数の値から組み立てるなら**枠付け**も
必須です——さもないと、ある値の末尾が別のフィールド名と偶然一致したときに衝突します：

```java
@Override
public String signature() {
    return part(sql.strip()) + part(autocommit ? "t" : "f");
}

private static String part(String text) {
    return text.length() + ":" + text;  // 長さを前置するので、ある値が次の値を装えない
}
```

### HistoryRepository

```java
public interface HistoryRepository {
    void initialize();
    void record(ExecutionRecord record);
    boolean wasExecuted(NodeId nodeId);
    List<NodeId> executedNodes();
    List<ExecutionRecord> allRecords();
    default List<ExecutionRecord> latestApplies() { /* walks allRecords() per id; override if cheaper */ }
    @Nullable ExecutionRecord findLatestRecord(NodeId nodeId);
}
```

## 能力インターフェース: ロールバックされるために実装が要るもの

ロールバックはタスクファイルの `down:` を実行しません。**履歴の行**が保持した payload を実行します。
実在するオブジェクトに対応しているのはそちらだからです——ファイルが述べているのは「いま適用するなら
何を作るか」であって、編集された瞬間に別物になります。これを担うのが 2 つの小さなインターフェースで、
どちらも実装しないプラグインは、マイグレーションを適用できてもう二度と取り出せません。

### `RollbackPayloadProvider` — up タスク側

行に何を残すべきかを、適用したタスク自身が報告します：

```java
public interface RollbackPayloadProvider {
    @Nullable String serializedDownTask();
    @Nullable String pluginMetadata();
}
```

`serializedDownTask` は素のテキストとして保存されます。止まったマイグレーションを調べる操作者が、列から
そのままコピーして読めるようにするためです（JDBC 系は `down:` の SQL をそのまま入れます）。
`pluginMetadata` はプラグインのもので、コアは中身を見ずに保存して返すだけです——符号化を知っているのは
書いた本人だけ。JDBC 系は `java.util.Properties` を使って `autocommit` を運びます。ロールバックに必要で、
SQL だけからは分からない値だからです。

up タスクは成功時に `TaskResult` を通じてこれを報告します。あわせてこのインターフェースを実装しておくと、
`amend` が**何も実行せずに**同じ問いを立てられます。実行されなかったマイグレーションについて完全な行を
書けるのはそのためで、実装していないプラグインは `amend` をまったく使えません。

### `DownTaskRestorer` — target 側

その payload を実行可能なものに戻します：

```java
public interface DownTaskRestorer {
    Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata);
}
```

`instanceof` で検出されるので、実装しない target は**そもそも何もロールバックできません**——実行の途中で
失敗するのではなく、型の上で見えます。返された `Task` は適用時とまったく同じ executor・listener・
履歴書き込みの経路を通ります。

```java
public final class MyTarget implements Target, DownTaskRestorer {
    @Override
    public Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata) {
        return new MyTask(this, serializedDownTask, MyOptions.decode(pluginMetadata));
    }
}
```

## 0.7.0 での変更

0.6.x 向けに書かれたプラグインはコンパイルが通りません（それが狙いです）。また 0.6.x 向けにビルドされた
**jar** は読み込まれたうえで最初の呼び出しで `AbstractMethodError` になるので、**再解決ではなく再ビルド**が
必要です。

| 旧 | 新 |
|---|---|
| `Environment`, `EnvironmentId`, `EnvironmentDefinition`, `EnvironmentProvider` | `Target`, `TargetId`, `TargetDefinition`, `TargetProvider` |
| `Optional<Task> downTask()` | `@Nullable Task downTask()` |
| — | `MigrationNode.fingerprint(Fingerprinter)`（必須） |
| — | `Task.signature()`（必須） |
| — | `MigrationNode.noWayBack()`（デフォルトあり） |
| `HistoryRepository` の読み取りは target id を取っていた | id だけで問われる。返る行が自分の target を持つ |
| `ExecutionRecord`, `TaskResult` | どちらも持つ要素が増えた（`origin`、`no_way_back` を含む） |

## 例: PostgreSQL プラグイン

完全な実装例として `migraphe-plugin-postgresql` モジュールを参照してください：
- `PostgreSQLPlugin` - メインプラグインクラス
- `PostgreSQLTargetProvider` - PostgreSQLTarget を生成
- `PostgreSQLMigrationNodeProvider` - PostgreSQLMigrationNode を生成
- `PostgreSQLHistoryRepositoryProvider` - PostgreSQLHistoryRepository を生成

## ベストプラクティス

1. **軽量な API 依存**: `migraphe-core` ではなく `migraphe-api` のみに依存する
2. **明確なエラーメッセージ**: 設定エラーには説明的な例外をスロー
3. **トランザクション管理**: Task 実装でトランザクションを適切に処理
4. **テスト**: 実際のデータベースインスタンスで統合テスト（例: Testcontainers）
5. **ServiceLoader 登録**: META-INF/services ファイルを忘れずに
6. **依存関係の処理**: 依存関係はフレームワークに任せ、タスク実行に集中する
7. **ロールバック**: マイグレーションが本当に一方向でない限り、`RollbackPayloadProvider` と
   `DownTaskRestorer` を実装する。無いと、適用はできても二度と取り出せないプロジェクトになり、
   `amend` で履歴を修復することもできません
