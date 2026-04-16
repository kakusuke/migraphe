# Migraphe サンプルプロジェクト

migraphe の**複数DB統一管理 + ドキュメント自動生成**機能を体感できるサンプルです。

同一のマイグレーション設定を **CLI** と **Gradle plugin** の両方から実行し、使用感を比較できるよう2つのサブプロジェクトに分かれています。

## ドメイン分割構成

e コマースの業務ドメインをDBごとに分割した、マイクロサービス寄りの現実的な構成です。

```
┌────────────────────────────────┐      ┌────────────────────────────────┐
│  MySQL (catalog DB)            │      │  PostgreSQL (commerce DB)      │
│  Catalog ドメイン              │      │  Commerce ドメイン             │
├────────────────────────────────┤      ├────────────────────────────────┤
│  currencies, locales           │◀─────│  users (preferred_currency,    │
│                                │      │         preferred_locale)      │
│  categories, brands            │      │  profiles, addresses           │
│  products, variants, images    │◀─────│  orders (currency_code)        │
│                                │      │  order_items (variant_id)      │
│  reviews ──────────────────────┼─────▶│  users (user_id)               │
│                                │      │  payment_methods, payments     │
└────────────────────────────────┘      └────────────────────────────────┘
                      ▲                                    ▲
                      └────────── 単一 DAG で管理 ────────┘
                            （cross-DB dependencies）
```

### 重要ポイント

- **タスクの `target:` と `dependencies:` は独立** — PG のタスクは MySQL のタスクに依存できる（その逆も可）
- migraphe は単一の DAG で両DBのマイグレーションを管理し、依存順に実行する
- **実行履歴は PG に集約** (`history.target: pg`) — `migraphe_history` テーブルに `environment_id` で区別して記録される
- **クロスDB参照はFKなし** — 異なるDB間にFKは張れないため、論理参照のみ（`reviews.user_id` は plain BIGINT）

## ディレクトリ構成

```
sample/
├── docker-compose.yml    # PostgreSQL 16 + MySQL 8.0
├── README.md             # このファイル
├── cli/                  # CLI 実行サンプル
│   └── README.md
└── gradle/               # Gradle plugin 実行サンプル
    └── README.md
```

## クイックスタート

### 1. データベースを起動

```bash
cd sample
docker compose up -d
```

PostgreSQL (`localhost:5432`, DB `commerce`, user `commerce`/`commerce_pass`) と MySQL (`localhost:3306`, DB `catalog`, user `catalog`/`catalog_pass`) が起動します。

### 2. どちらかのサンプルを試す

- **CLI**: [cli/README.md](cli/README.md) を参照
- **Gradle**: [gradle/README.md](gradle/README.md) を参照

### 3. 後片付け

```bash
cd sample
docker compose down -v
```

## CLI と Gradle の使い分け

| | CLI | Gradle Plugin |
|---|-----|--------------|
| 起動方法 | `migraphe up` | `./gradlew migrapheUp` |
| 事前準備 | `./gradlew :migraphe-cli:installDist` 必要 | `includeBuild` により不要 |
| 導入先 | CI/CD、スタンドアロン運用、Docker image | 既存の Gradle ビルドへの統合 |
| プラグイン解決 | Maven Central から取得（`migraphe.yaml` の `plugins:` で指定） | Gradle の `migraphePlugin` configuration |

どちらも同じマイグレーション設定（`migraphe.yaml`, `targets/`, `tasks/`）を使います。
