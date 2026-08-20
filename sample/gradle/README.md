# Gradle プラグインサンプル — e コマース複数DB マイグレーション

migraphe Gradle プラグイン (`io.github.kakusuke.migraphe`) で PostgreSQL (commerce) + MySQL (catalog) を**単一DAG**として管理するデモです。

## 前提条件

- Java 21
- Docker / Docker Compose
- ネットワーク接続（プラグインを JitPack から解決します）

## セットアップ

### データベース起動

```bash
cd ..                    # sample/ へ
docker compose up -d
```

Gradle プラグイン本体と DB プラグインは JitPack (`https://jitpack.io`) から自動的に解決されます。`settings.gradle.kts` および `build.gradle.kts` を参照してください。

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
├── settings.gradle.kts    # JitPack からプラグイン解決 (pluginManagement + resolutionStrategy)
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
| 解決元 | JitPack (+ `~/.m2` キャッシュ) | JitPack (+ `~/.m2` キャッシュ) |
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

- **`Plugin [id: 'io.github.kakusuke.migraphe'] was not found`**: JitPack 側で `v0.6.0` のビルドが未完了。<https://jitpack.io/#kakusuke/migraphe> で Build Log を確認してください。
- **接続エラー**: `docker compose ps` で両 DB が `healthy` になっているか確認。
- **プラグイン変更が反映されない**: `~/.m2/repository/com/github/kakusuke/migraphe/` を削除 → `./gradlew --refresh-dependencies migrapheValidate` で再取得。
