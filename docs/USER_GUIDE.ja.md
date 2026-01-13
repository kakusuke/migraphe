# Migraphe ユーザーガイド

[English version](USER_GUIDE.md)

## 目次

1. [はじめに](#はじめに)
2. [インストール](#インストール)
3. [プロジェクトのセットアップ](#プロジェクトのセットアップ)
4. [設定](#設定)
5. [マイグレーションの作成](#マイグレーションの作成)
6. [マイグレーションの実行](#マイグレーションの実行)
7. [環境管理](#環境管理)
8. [高度な機能](#高度な機能)
9. [トラブルシューティング](#トラブルシューティング)

## はじめに

Migrapheは、複数の環境にわたる複雑なデータベースマイグレーションを管理するために設計されたマイグレーションオーケストレーションツールです。マイグレーションタスク間の依存関係を表現するために有向非巡回グラフ（DAG）を使用し、正しい順序で実行されることを保証します。

### 主要な概念

- **マイグレーションタスク**: 単一のマイグレーション作業単位（例: テーブルの作成）
- **ターゲット**: データベース接続設定
- **環境**: 実行コンテキスト（開発、ステージング、本番）
- **タスクID**: ファイルパスから自動生成（例: `tasks/db1/001_create_users.yaml` → `db1/001_create_users`）
- **依存関係**: 実行順序を決定するタスク間の関係
- **履歴**: データベースに保存された実行済みマイグレーションの記録

## インストール

### 前提条件

- Java 21以降
- PostgreSQLデータベース

### ソースからビルド

```bash
# リポジトリをクローン
git clone https://github.com/yourusername/migraphe.git
cd migraphe

# Fat JARをビルド
./gradlew fatJar

# 実行可能JARが以下に作成されます:
# migraphe-cli/build/libs/migraphe-cli-all.jar
```

### エイリアスの作成（オプション）

便利のため、シェルにエイリアスを作成します:

```bash
# ~/.bashrc または ~/.zshrc に追加
alias migraphe='java -jar /path/to/migraphe-cli-all.jar'

# シェル設定を再読み込み
source ~/.bashrc  # または source ~/.zshrc

# これで以下のように使用できます:
migraphe status
migraphe up
```

## プロジェクトのセットアップ

### ディレクトリ構造

マイグレーションプロジェクト用に以下のディレクトリ構造を作成します:

```
my-project/
├── migraphe.yaml              # プロジェクト設定
├── targets/                   # データベース接続設定
│   ├── db1.yaml
│   ├── db2.yaml
│   └── history.yaml
├── tasks/                     # マイグレーションタスク定義
│   ├── db1/
│   │   ├── 001_create_schema.yaml
│   │   ├── 002_create_users.yaml
│   │   └── 003_create_posts.yaml
│   └── db2/
│       └── 001_initial_schema.yaml
└── environments/              # オプション: 環境固有のオーバーライド
    ├── development.yaml
    └── production.yaml
```

### 最小限必要なファイル

最小限、以下が必要です:

1. `migraphe.yaml` - プロジェクト設定
2. `targets/history.yaml` - 履歴保存設定
3. 少なくとも1つのターゲットファイル（例: `targets/db1.yaml`）
4. 少なくとも1つのタスクファイル（例: `tasks/db1/001_initial.yaml`）

## 設定

### プロジェクト設定（`migraphe.yaml`）

```yaml
project:
  name: my-project

history:
  target: history  # 実行履歴を保存するターゲット名
```

**フィールド:**
- `project.name`（必須）: プロジェクト識別子
- `history.target`（必須）: マイグレーション履歴を保存するターゲット名

### ターゲット設定

ターゲットファイルはデータベース接続を定義します。`targets/`ディレクトリに配置します。

**例: `targets/db1.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

**フィールド:**
- `type`（必須）: データベースタイプ（現在は`postgresql`のみサポート）
- `jdbc_url`（必須）: JDBC接続URL
- `username`（必須）: データベースユーザー名
- `password`（必須）: データベースパスワード

注: ターゲット名はファイル名から導出されます（例: `db1.yaml` → ターゲット名 `db1`）。

**例: `targets/history.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
username: historyuser
password: historypass
```

### タスク設定

タスクファイルは個別のマイグレーションを定義します。`tasks/`ディレクトリに配置します。

**タスクIDの生成:**
タスクIDは`tasks/`からの相対ファイルパスから自動生成されます:
- `tasks/db1/001_create_users.yaml` → タスクID: `db1/001_create_users`
- `tasks/db1/schema/initial.yaml` → タスクID: `db1/schema/initial`

**例: `tasks/db1/001_create_users.yaml`**

```yaml
name: Create users table
target: db1
up: |
  CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
down: |
  DROP TABLE IF EXISTS users;
```

**フィールド:**
- `name`（必須）: 人間が読めるタスク説明
- `target`（必須）: ターゲット名（ターゲット設定と一致する必要があります）
- `dependencies`（オプション）: このタスクが依存するタスクIDのリスト
- `up`（必須）: フォワードマイグレーション用に実行するSQL
- `down`（オプション）: ロールバック用に実行するSQL

### 環境固有の設定

環境ファイルは、特定の環境用にベース設定をオーバーライドします。

**例: `environments/production.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://prod-db.example.com:5432/mydb
    password: ${DB_PASSWORD}  # 環境変数の置換
```

`${VAR}`を使用した変数置換はMicroProfile Configによりサポートされています。

## マイグレーションの作成

### 基本的なマイグレーション

```yaml
name: Create posts table
target: db1
up: |
  CREATE TABLE posts (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
down: |
  DROP TABLE IF EXISTS posts;
```

### 依存関係のあるマイグレーション

```yaml
name: Create comments table
target: db1
dependencies:
  - db1/001_create_users
  - db1/002_create_posts
up: |
  CREATE TABLE comments (
    id SERIAL PRIMARY KEY,
    post_id INTEGER REFERENCES posts(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
down: |
  DROP TABLE IF EXISTS comments;
```

### 複数ステートメントのマイグレーション

PostgreSQLはトランザクショナルDDLをサポートしているため、複数のステートメントも安全です:

```yaml
name: Add indexes
target: db1
dependencies:
  - db1/001_create_users
up: |
  CREATE INDEX idx_users_email ON users(email);
  CREATE INDEX idx_users_created_at ON users(created_at);

  COMMENT ON TABLE users IS 'User account information';
  COMMENT ON COLUMN users.email IS 'Unique user email address';
down: |
  DROP INDEX IF EXISTS idx_users_email;
  DROP INDEX IF EXISTS idx_users_created_at;
```

### ベストプラクティス

1. **常にDOWNマイグレーションを提供する**: ロールバック機能を有効にします
2. **連番を使用する**: 順序が明確になります（001, 002, 003...）
3. **タスクごとに1つの論理的変更**: 理解とロールバックが容易になります
4. **説明的な名前を使用する**: 明確なタスク名で可読性が向上します
5. **マイグレーションをローカルでテストする**: UPとDOWNの両方が正しく動作することを確認します

## マイグレーションの実行

### マイグレーションステータスの確認

```bash
java -jar migraphe-cli-all.jar status
```

**出力:**
```
Migration Status
================

[ ] db1/001_create_users - Create users table
[ ] db1/002_create_posts - Create posts table
[✓] db1/003_create_comments - Create comments table

Summary:
  Total: 3
  Executed: 1
  Pending: 2
```

### マイグレーションの実行

```bash
java -jar migraphe-cli-all.jar up
```

**出力:**
```
Executing migrations...

Execution Plan:
  Levels: 2
  Total Tasks: 2

Level 0:
  [RUN]  Create users table ... OK (45ms)

Level 1:
  [RUN]  Create posts table ... OK (32ms)

Migration completed successfully. 2 migrations executed.
```

### 環境固有の実行

```bash
# 本番環境のオーバーライドを読み込む
java -jar migraphe-cli-all.jar up --env production

# 開発環境のオーバーライドを読み込む
java -jar migraphe-cli-all.jar up --env development
```

## 環境管理

### 開発環境

**`environments/development.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://localhost:5432/mydb_dev
    username: devuser
    password: devpass

  history:
    jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history_dev
```

### 本番環境

**`environments/production.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://prod-db.company.com:5432/mydb
    username: produser
    password: ${PROD_DB_PASSWORD}  # 環境変数から

  history:
    jdbc_url: jdbc:postgresql://prod-db.company.com:5432/migraphe_history
    password: ${PROD_HISTORY_PASSWORD}
```

### 環境変数の使用

実行前に環境変数を設定します:

```bash
export PROD_DB_PASSWORD=secretpassword
export PROD_HISTORY_PASSWORD=historypassword

java -jar migraphe-cli-all.jar up --env production
```

## 高度な機能

### 並列実行

Migrapheは同じ依存レベルにある独立したマイグレーションを自動的に並列化します:

```
Level 0（並列実行）:
  - db1/001_create_users
  - db2/001_create_products

Level 1（Level 0の後に並列実行）:
  - db1/002_create_posts (db1/001_create_usersに依存)
  - db2/002_create_orders (db2/001_create_productsに依存)
```

### 複雑な依存関係グラフ

複雑な依存関係構造を作成できます:

```yaml
# tasks/db1/005_final_setup.yaml
name: Final setup
target: db1
dependencies:
  - db1/001_create_users
  - db1/002_create_posts
  - db1/003_create_comments
  - db1/004_add_indexes
up: |
  -- すべての前のマイグレーションが必要な最終セットアップ
  CREATE VIEW recent_posts AS
  SELECT p.*, u.name as author_name
  FROM posts p
  JOIN users u ON p.user_id = u.id
  WHERE p.created_at > NOW() - INTERVAL '30 days';
down: |
  DROP VIEW IF EXISTS recent_posts;
```

### 実行履歴

マイグレーション履歴は`migraphe_history`テーブルに保存されます:

```sql
-- 実行履歴を照会
SELECT * FROM migraphe_history
ORDER BY executed_at DESC;

-- 特定のマイグレーションを確認
SELECT * FROM migraphe_history
WHERE node_id = 'db1/001_create_users';
```

**履歴テーブルスキーマ:**
- `id`: 一意の実行ID（UUID）
- `node_id`: タスクID
- `environment_id`: 環境名
- `direction`: UPまたはDOWN
- `status`: SUCCESS、FAILURE、またはSKIPPED
- `description`: タスク名
- `executed_at`: 実行タイムスタンプ
- `duration_ms`: 実行時間
- `serialized_down_task`: ロールバックSQL（UPマイグレーションのみ）
- `error_message`: エラーの詳細（FAILUREステータスのみ）

## トラブルシューティング

### よくある問題

#### 1. "Target not found" エラー

**問題:**
```
Error: Target 'db1' not found in configuration
```

**解決策:**
- `targets/db1.yaml`が存在することを確認
- ターゲット名が正確に一致することを確認（大文字小文字を区別）
- YAML構文が正しいことを確認

#### 2. "Cyclic dependency detected" エラー

**問題:**
```
Error: Cyclic dependency detected in migration graph
```

**解決策:**
- タスクの依存関係を確認
- 循環参照を削除
- 依存関係はDAG（有向非巡回グラフ）を形成する必要があります

#### 3. 接続失敗

**問題:**
```
Error: Could not connect to database
```

**解決策:**
- データベースが実行中であることを確認
- JDBC URL、ユーザー名、パスワードを確認
- 手動で接続をテスト: `psql -h localhost -U myuser -d mydb`
- ファイアウォール設定を確認

#### 4. マイグレーション既に実行済み

**動作:**
Migrapheは既に実行されたマイグレーションを自動的にスキップします:

```
Level 0:
  [SKIP] Create users table (already executed)
```

これは期待される動作です。再実行するには、履歴から手動で削除します:

```sql
DELETE FROM migraphe_history WHERE node_id = 'db1/001_create_users';
```

#### 5. マイグレーション失敗

**問題:**
```
Level 0:
  [FAIL] Create users table - ERROR: syntax error at or near "CRATE"
```

**解決策:**
- タスクファイルのSQL構文を修正
- 履歴から失敗したレコードを削除
- マイグレーションを再実行

```sql
-- エラーの詳細を確認
SELECT error_message FROM migraphe_history
WHERE node_id = 'db1/001_create_users' AND status = 'FAILURE';

-- 再試行のため失敗したレコードを削除
DELETE FROM migraphe_history
WHERE node_id = 'db1/001_create_users' AND status = 'FAILURE';
```

### デバッグのヒント

1. **設定の読み込みを確認:**
   ```bash
   # 詳細ログ追加（将来の機能）
   java -jar migraphe-cli-all.jar status --verbose
   ```

2. **YAML構文を検証:**
   ```bash
   # yamllintまたは類似ツールを使用
   yamllint migraphe.yaml targets/ tasks/
   ```

3. **データベース接続をテスト:**
   ```bash
   psql -h localhost -U myuser -d mydb
   ```

4. **実行履歴を確認:**
   ```sql
   SELECT node_id, status, executed_at, duration_ms, error_message
   FROM migraphe_history
   ORDER BY executed_at DESC
   LIMIT 10;
   ```

## 次のステップ

- 設計の詳細については[アーキテクチャドキュメント](../CLAUDE.md)を参照
- 翻訳については[英語版ユーザーガイド](USER_GUIDE.md)を確認
- `examples/`ディレクトリのサンプルプロジェクトを確認（利用可能な場合）

## サポート

問題や質問については:
- GitHub Issues: [リポジトリURL]
- ドキュメント: [ドキュメントURL]
