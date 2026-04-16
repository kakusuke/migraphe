# Gradle プラグインサンプル — e コマース複数DB マイグレーション

migraphe Gradle プラグイン (`io.github.kakusuke.migraphe`) で PostgreSQL (commerce) + MySQL (catalog) を**単一DAG**として管理するデモです。

## 前提条件

- Java 21
- Docker / Docker Compose
- リポジトリのルート（このサンプルの親の親）が migraphe のソースツリーであること

## セットアップ

### 1. データベース起動

```bash
cd ..                    # sample/ へ
docker compose up -d
```

### 2. プラグインをローカル Maven に公開

このサンプルは `mavenLocal()` から Gradle プラグイン本体と DB プラグインを解決します。リポジトリルートで一度だけ実行:

```bash
cd ../../                # リポジトリルートへ
./gradlew publishToMavenLocal
```

これ以降、このサンプルは完全にスタンドアロンに動作します（親プロジェクトの変更を取り込むには再実行が必要）。

## 実行例

```bash
cd sample/gradle
```

### 設定検証（DB接続不要）

```bash
./gradlew migrapheValidate
```

### マイグレーション計画のプレビュー

```bash
./gradlew migrapheUp --preview
```

PG と MySQL のタスクが単一の DAG として並び、依存順で表示されます。

### 実行

```bash
./gradlew migrapheUp
```

### 状態確認

```bash
./gradlew migrapheStatus
```

### ドキュメント生成

```bash
./gradlew migrapheGenerate
```

特定のジェネレーターだけ動かすには `--name` を使います。

```bash
./gradlew migrapheGenerate --name pg-schema-docs
```

### ロールバック

```bash
./gradlew migrapheDown --all
```

## プロジェクト構造

```
gradle/
├── settings.gradle.kts    # mavenLocal() からプラグイン解決
├── build.gradle.kts       # id("io.github.kakusuke.migraphe") 適用 + migraphePlugin 依存
├── gradlew, gradlew.bat, gradle/wrapper/   # Gradle wrapper
├── migraphe.yaml          # project.name, history.target, generators
├── targets/
│   ├── pg.yaml            # PostgreSQL 接続情報
│   └── mysql.yaml         # MySQL 接続情報
└── tasks/
    ├── pg/                # Commerce ドメイン（9タスク）
    └── mysql/             # Catalog ドメイン（10タスク）
```

CLI サンプル (`sample/cli/`) とタスク内容は同一です。適用方法（`./gradlew` vs `migraphe`）を切り替えて比較してください。

## CLI との主な違い

| | CLI | Gradle Plugin |
|---|-----|--------------|
| プラグイン宣言 | `migraphe.yaml` の `plugins:` セクション | `build.gradle.kts` の `migraphePlugin(...)` |
| 解決元 | `~/.m2` + Maven Central | `~/.m2` (Maven Local) |
| 実行コマンド | `migraphe <cmd>` | `./gradlew migraphe<Cmd>` |
| 既存Gradleビルドへの統合 | 別プロセス | 同一ビルド内タスク |

## クロスDB依存の仕組み

`sample/README.md` と `sample/cli/README.md` を参照してください。タスクは両サンプルで同一です。

## クリーンアップ

```bash
cd ../                   # sample/ へ
docker compose down -v
```

## トラブルシューティング

- **`Plugin [id: 'io.github.kakusuke.migraphe'] was not found`**: ルートで `./gradlew publishToMavenLocal` を実行していない。
- **接続エラー**: `docker compose ps` で両 DB が `healthy` になっているか確認。
- **プラグイン変更が反映されない**: `~/.m2/repository/io/github/kakusuke/migraphe/` を削除 → ルートで `./gradlew publishToMavenLocal` を再実行。
