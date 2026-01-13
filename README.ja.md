# Migraphe

有向非巡回グラフ（DAG）構造を使用して、複数環境にわたるデータベースおよびインフラストラクチャのマイグレーションを管理するオーケストレーションツールです。

[English README](README.md)

## 機能

- **DAGベースのマイグレーション**: マイグレーションタスク間の複雑な依存関係を定義
- **マルチ環境サポート**: 開発、ステージング、本番環境のマイグレーションを管理
- **プラガブルアーキテクチャ**: PostgreSQLをサポート、他のデータベースへの拡張性
- **YAML設定**: シンプルで読みやすい設定ファイル
- **実行履歴**: マイグレーション実行履歴の追跡とロールバックサポート
- **型安全**: Java 21で構築、モダンな言語機能を活用

## クイックスタート

### 前提条件

- Java 21以降
- PostgreSQLデータベース（マイグレーション実行用）

### ビルド

```bash
./gradlew fatJar
```

これにより、スタンドアロンのJARファイルが`migraphe-cli/build/libs/migraphe-cli-all.jar`に作成されます。

### プロジェクトの作成

1. プロジェクトディレクトリを作成:

```bash
mkdir my-project
cd my-project
```

2. 設定構造を作成:

```bash
mkdir -p targets tasks/db1
```

3. `migraphe.yaml`を作成:

```yaml
project:
  name: my-project

history:
  target: history
```

4. `targets/db1.yaml`を作成:

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

5. `targets/history.yaml`を作成:

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
username: myuser
password: mypassword
```

6. `tasks/db1/001_create_users.yaml`を作成:

```yaml
name: Create users table
target: db1
up: |
  CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
  );
down: |
  DROP TABLE IF EXISTS users;
```

### マイグレーションの実行

```bash
# マイグレーションステータスの確認
java -jar path/to/migraphe-cli-all.jar status

# マイグレーションの実行
java -jar path/to/migraphe-cli-all.jar up
```

## ドキュメント

- [ユーザーガイド（日本語）](docs/USER_GUIDE.ja.md) - 詳細なドキュメント
- [User Guide (English)](docs/USER_GUIDE.md) - English documentation

## プロジェクト構造

```
my-project/
├── migraphe.yaml              # プロジェクト設定
├── targets/                   # データベース接続設定
│   ├── db1.yaml
│   └── history.yaml
├── tasks/                     # マイグレーションタスク定義
│   ├── db1/
│   │   ├── 001_create_users.yaml
│   │   └── 002_create_posts.yaml
│   └── db2/
│       └── 001_initial_schema.yaml
└── environments/              # オプション: 環境固有のオーバーライド
    ├── development.yaml
    └── production.yaml
```

## アーキテクチャ

Migrapheは以下で構築されています:

- **ドメイン駆動設計（DDD）**: 明確な関心の分離
- **インターフェース駆動アーキテクチャ**: プラガブルコンポーネント
- **Java 21**: モダンな言語機能（record、sealed interface、パターンマッチング）
- **MicroProfile Config**: 型安全な設定管理
- **NullAway + jspecify**: コンパイル時のnull安全性チェック
- **Gradle**: Kotlin DSLによるビルド自動化

### コア概念

- **MigrationNode**: 依存関係を持つ単一のマイグレーションタスク
- **MigrationGraph**: 実行順序を保証するDAG
- **Environment**: データベース接続設定
- **Task**: 実行可能なマイグレーションロジック（up/down）
- **HistoryRepository**: 実行済みマイグレーションの追跡

## 開発

### ソースからビルド

```bash
# リポジトリをクローン
git clone https://github.com/yourusername/migraphe.git
cd migraphe

# プロジェクトをビルド
./gradlew build

# テストを実行
./gradlew test

# コードフォーマットを適用
./gradlew spotlessApply
```

### テストの実行

```bash
# すべてのテスト
./gradlew test

# 特定のモジュール
./gradlew :migraphe-core:test
./gradlew :migraphe-postgresql:test
./gradlew :migraphe-cli:test
```

テストカバレッジ: 177+テスト、100%合格

## コントリビューション

このプロジェクトは以下に従います:

- **TDD（テスト駆動開発）**: すべての機能はテストファースト
- **コードフォーマット**: SpotlessとGoogle Java Format
- **100%テスト合格率**: コミット前にすべてのテストが合格必須

## ライセンス

[Your License Here]

## リンク

- [ユーザーガイド](docs/USER_GUIDE.ja.md)
- [アーキテクチャドキュメント](CLAUDE.md) - 詳細な設計決定
