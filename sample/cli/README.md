# CLI サンプル — e コマース複数DB マイグレーション

`migraphe` CLI で PostgreSQL (commerce) + MySQL (catalog) の2つのDBを**単一DAG**として管理するデモです。ドキュメント生成まで含みます。

## 前提条件

- Java 21
- Docker / Docker Compose
- リポジトリのルート（親の親ディレクトリ）が migraphe のソースツリーであること

## セットアップ

### 1. データベース起動

```bash
cd ..                    # sample/ へ
docker compose up -d
```

### 2. CLI 実行スクリプトを生成

```bash
cd ../../                # リポジトリルートへ
./gradlew :migraphe-cli:installDist
```

`migraphe-cli/build/install/migraphe/bin/migraphe` が生成されます。

### 3. エイリアス設定（任意だが推奨）

```bash
cd sample/cli
export MIGRAPHE=../../migraphe-cli/build/install/migraphe/bin/migraphe
alias migraphe="$MIGRAPHE"
```

### 4. プラグインを JitPack から解決してロックファイルを生成

`migraphe.yaml` の `plugins:` は JitPack から解決します。初回のみロックファイルを生成してください。

```bash
migraphe pin
```

`migraphe.lock.yaml` が生成され、以降の `migraphe validate` / `migraphe up` / `migraphe down` でプラグインの SHA-256 が照合されます。

## 実行例

### 設定検証（DB接続不要）

```bash
migraphe validate
```

全タスクの YAML 構文、依存関係、target 参照を検証します。

### マイグレーション計画のプレビュー

```bash
migraphe up --dry-run
```

PG と MySQL のタスクが単一の DAG として並び、依存順（MySQL の `currencies` → PG の `users` → MySQL の `reviews` ...）で表示されます。

### 実行

```bash
migraphe up -y
```

各タスクはそれぞれの DB に対して実行されます。実行履歴は PG の `migraphe_history` テーブルに `environment_id` 列で区別して集約されます。

### 状態確認

```bash
migraphe status
```

### ドキュメント生成

```bash
migraphe generate
```

以下が出力されます。

- `docs/postgresql/` — PostgreSQL の Commerce スキーマの Markdown
- `docs/mysql/` — MySQL の Catalog スキーマの Markdown
- `docs/migration-tree.json` — 全 DAG 構造の JSON 表現

特定のジェネレーターだけ動かすには `--name` を使います。

```bash
migraphe generate --name pg-schema-docs
```

### ロールバック

```bash
migraphe down --all -y
```

依存の逆順で両DBのテーブルが落ちます。

## プロジェクト構造

```
cli/
├── migraphe.yaml          # plugins, project.name, history.target, generators
├── targets/
│   ├── pg.yaml            # PostgreSQL 接続情報
│   └── mysql.yaml         # MySQL 接続情報
└── tasks/
    ├── pg/                # Commerce ドメイン（9タスク）
    │   ├── 02_users/
    │   ├── 05_orders/
    │   ├── 06_payments/
    │   └── 07_indexes/
    └── mysql/             # Catalog ドメイン（10タスク）
        ├── 01_common/
        ├── 02_catalog/
        ├── 03_reviews/
        └── 04_indexes/
```

## クロスDB依存の仕組み

タスクの `target:` はそのタスクが実行されるDBを指定しますが、`dependencies:` は**任意のタスクID**を参照できます。例:

```yaml
# pg/02_users/001_users.yaml
target: pg
dependencies:
  - mysql/01_common/001_currencies   # ← MySQL のタスクに依存
  - mysql/01_common/002_locales
```

migraphe は単一の DAG でトポロジカルソートし、正しい順序で各タスクを該当DBで実行します。FK 制約はDBをまたげないため、参照カラムは論理参照（VARCHAR / BIGINT）としています。

## クリーンアップ

```bash
cd ../                   # sample/ へ
docker compose down -v
```

## トラブルシューティング

- **`Plugin not found` エラー**: `migraphe pin` を実行していない、または JitPack 側でビルドが未完了（<https://jitpack.io/#kakusuke/migraphe> で `main-SNAPSHOT` の Build Log を確認）。
- **接続エラー**: `docker compose ps` で両 DB が `healthy` になっているか確認。
- **ポート競合**: ローカルに PostgreSQL/MySQL が動いている場合は停止するか、`docker-compose.yml` のポートを変更。
