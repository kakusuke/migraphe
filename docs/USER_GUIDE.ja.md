# Migraphe ユーザーガイド

[English version](USER_GUIDE.md)

## 目次

1. [はじめに](#はじめに)
2. [インストール](#インストール)
3. [プロジェクトのセットアップ](#プロジェクトのセットアップ)
4. [設定](#設定)
5. [マイグレーションの作成](#マイグレーションの作成)
6. [マイグレーションの実行](#マイグレーションの実行)
7. [ロールバック（down）](#ロールバックdown)
8. [設定の検証（validate）](#設定の検証validate)
9. [スキーマドキュメント生成（generate）](#スキーマドキュメント生成generate)
10. [環境管理](#環境管理)
11. [高度な機能](#高度な機能)
12. [Gradleプラグイン](#gradleプラグイン)
13. [トラブルシューティング](#トラブルシューティング)

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
- サポート対象のデータベース（PostgreSQL、MySQL 8.0+、または任意のJDBC対応データベース）

### リリース成果物のダウンロード（推奨）

```bash
# tar.gz — Linux / macOS
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.1.0/migraphe-0.1.0.tar.gz | tar xz
export PATH="$PWD/migraphe/bin:$PATH"

# zip — Windows
curl -L -o migraphe.zip https://github.com/kakusuke/migraphe/releases/download/v0.1.0/migraphe-0.1.0.zip
unzip migraphe.zip
export PATH="$PWD/migraphe/bin:$PATH"

# fat JAR — 単一ファイル
curl -L -o migraphe.jar https://github.com/kakusuke/migraphe/releases/download/v0.1.0/migraphe-0.1.0-all.jar
alias migraphe="java -jar $PWD/migraphe.jar"
```

### ソースからビルド

```bash
# リポジトリをクローン
git clone https://github.com/kakusuke/migraphe.git
cd migraphe

# CLI をビルド
./gradlew :migraphe-cli:installDist

# CLI が以下に作成されます:
# migraphe-cli/build/install/migraphe/bin/migraphe
export PATH="$PWD/migraphe-cli/build/install/migraphe/bin:$PATH"
```

以降の例はすべて `migraphe` コマンドが `PATH` 上にある前提です。

### プラグインのインストール

Migraphe はプラグインアーキテクチャを採用しており、データベースサポートは別のプラグインとして提供されます。

**現在利用可能なプラグイン:**

| プラグイン | タイプ | 説明 |
|-----------|--------|------|
| `migraphe-plugin-postgresql` | `postgresql` | PostgreSQL データベースサポート（`postgresql-schema` ソースおよび `postgresql-markdown` アウトプットプラグインを含む） |
| `migraphe-plugin-mysql` | `mysql` | MySQL 8.0+ データベースサポート（`mysql-schema` ソースおよび `mysql-markdown` アウトプットプラグインを含む） |
| `migraphe-plugin-jdbc` | `jdbc` | 汎用 JDBC サポート（任意の JDBC データベースで使用可能） |
| `migraphe-plugin-generator-json` | `output-json` | JSON 出力ジェネレータプラグイン |

#### 方法1: Maven 座標（推奨）

`migraphe.yaml` に `plugins` セクションを追加し、Maven 座標を記述します。Migraphe のプラグインは JitPack 経由で配布されているため、JitPack リポジトリを宣言したうえで map 形式で参照します:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.2.0
    repository: jitpack
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.2.0
    repository: jitpack

project:
  name: my-project
history:
  target: history
```

`maven-central` は常に暗黙的に利用可能なので再宣言は不要です。推移的依存（JDBC ドライバ、Jackson 等）は Maven Central から自動的に解決されます。

##### 追加のリポジトリ

他の HTTPS Maven リポジトリも同じ書き方で追加でき、プラグインごとに参照先を選択できます:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io
  - id: my-internal
    url: https://maven.internal.example.com/releases

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.2.0
    repository: jitpack
  - coordinate: com.example:internal-plugin:1.0.0
    repository: my-internal
```

##### ロックファイル（`migraphe.lock.yaml`）

Migraphe は全プラグインと推移的依存 JAR を SHA-256 でロックファイルに固定します。**`plugins:` を宣言する場合は必ずロックファイルが必要** で、無いと CLI は起動を拒否します。

`plugins:` を編集したら次のコマンドでロックファイルを生成または更新します:

```bash
migraphe pin
```

各プラグインを設定済みリポジトリから解決し、全 JAR の SHA-256 を計算して `migraphe.lock.yaml` に書き出します。`migraphe.yaml` と一緒にバージョン管理してください。

CI ではロックファイルが最新かを書き込みなしで検証する `--check` を使います:

```bash
migraphe pin --check
```

ロックファイルが無い、または再解決した結果と差異がある場合は非ゼロ終了します。`migraphe validate` もオフラインで lock 整合チェックを実施します。

ピン留め後に JAR が改ざんされた場合（例: ローカルキャッシュの破損）、起動時に対応座標を含む checksum mismatch エラーで失敗します。

#### 方法2: plugins/ ディレクトリ（レガシー）

プラグイン JAR ファイルをプロジェクトの `plugins/` ディレクトリに直接配置します:

```
my-project/
├── migraphe.yaml
├── plugins/                      # プラグインディレクトリ
│   └── migraphe-plugin-postgresql-x.x.x.jar
├── targets/
└── tasks/
```

**注意:** 両方の方法を同時に使用できます。Maven で解決されたプラグインが先に読み込まれ、次に `plugins/` ディレクトリが読み込まれます。

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
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.2.0
    repository: jitpack

project:
  name: my-project

history:
  target: history  # 実行履歴を保存するターゲット名
```

**フィールド:**
- `plugins`（任意）: CLI プラグイン解決用の Maven 座標リスト（`groupId:artifactId:version`）
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
- `type`（必須）: データベースタイプ（`postgresql`、`mysql`、または `jdbc`）
- `jdbc_url`（必須）: JDBC接続URL
- `username`（必須）: データベースユーザー名
- `password`（必須）: データベースパスワード
- `driver_class`（`jdbc` タイプの場合は必須）: JDBCドライバの完全修飾クラス名
- `db_label`（オプション、`jdbc` タイプのみ）: データベースの表示ラベル（例: "MariaDB"）

注: ターゲット名はファイル名から導出されます（例: `db1.yaml` → ターゲット名 `db1`）。

**例: `targets/history.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
username: historyuser
password: historypass
```

**例: MySQL ターゲット（`targets/mysql_db.yaml`）**

```yaml
type: mysql
jdbc_url: jdbc:mysql://localhost:3306/myapp
username: dbuser
password: secret
```

**例: 汎用 JDBC ターゲット（`targets/mariadb.yaml`）**

```yaml
type: jdbc
driver_class: org.mariadb.jdbc.Driver
db_label: MariaDB
jdbc_url: jdbc:mariadb://localhost:3306/myapp
username: user
password: secret
```

汎用 JDBC プラグイン（`type: jdbc`）は任意の JDBC 対応データベースで使用できます。`driver_class` を指定し、JDBC ドライバ JAR がクラスパスで利用可能であることを確認してください。

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
- `autocommit`（オプション）: トランザクションなしで実行（[Autocommitモード](#autocommitモード)を参照）

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

### Autocommitモード

一部のSQL文はトランザクション内で実行できません。そのような場合は `autocommit: true` を使用します:

**一般的なユースケース:**
- `CREATE DATABASE` / `DROP DATABASE`
- `CREATE INDEX CONCURRENTLY`
- `VACUUM`
- `CLUSTER`

**例: データベースの作成**

```yaml
# tasks/admin/001_create_database.yaml
name: Create application database
target: admin
autocommit: true
up: |
  CREATE DATABASE myapp;
down: |
  DROP DATABASE myapp;
```

**重要な注意事項:**
- Autocommitマイグレーションは失敗時の自動ロールバックがありません
- SQLが途中で失敗した場合、部分的な変更が残る可能性があります
- 必要な場合にのみ注意して使用してください

### ベストプラクティス

1. **常にDOWNマイグレーションを提供する**: ロールバック機能を有効にします
2. **連番を使用する**: 順序が明確になります（001, 002, 003...）
3. **タスクごとに1つの論理的変更**: 理解とロールバックが容易になります
4. **説明的な名前を使用する**: 明確なタスク名で可読性が向上します
5. **マイグレーションをローカルでテストする**: UPとDOWNの両方が正しく動作することを確認します

## マイグレーションの実行

### マイグレーションステータスの確認

```bash
migraphe status
```

**出力:**
```
Migration Status
================

● [ ] db1/001_create_users - Create users table
│
● [ ] db1/002_create_posts - Create posts table
│
● [✓] db1/003_create_comments - Create comments table (58ms, 2026-01-23 10:30:00)

Summary: Total: 3 | Executed: 1 | Pending: 2
```

### マイグレーションの実行

```bash
# 全ての保留中のマイグレーションを実行
migraphe up

# 確認プロンプトをスキップ
migraphe up -y

# 実行計画のみ表示（実際には実行しない）
migraphe up --preview

# 特定のマイグレーションまで実行（指定IDとその依存先のみ）
migraphe up <id>

# オプションの組み合わせ
migraphe up -y --preview db1/002_create_posts
```

**出力例:**
```
Migrations to execute:

● [ ] db1/001_create_users - Create users table
│
● [ ] db1/002_create_posts - Create posts table

2 migrations will be executed.

Proceed? [y/N]: y

Executing migrations...

[OK]   Create users table (45ms)
[OK]   Create posts table (32ms)

Migration completed successfully. 2 migrations executed.
```

### コマンドオプション

| オプション | 説明 |
|-----------|------|
| `<id>` | 指定したマイグレーションとその依存先のみを実行 |
| `-y` | 確認プロンプトをスキップ |
| `--preview` | 実行計画のみ表示し、実際には実行しない |

### 色付き出力

マイグレーション結果は色付きで表示されます:

- **[OK]** (緑): マイグレーション成功
- **[SKIP]** (黄): 既に実行済みでスキップ
- **[FAIL]** (赤): マイグレーション失敗

色出力は `NO_COLOR` 環境変数を設定することで無効にできます。

### 失敗時の詳細表示

マイグレーションが失敗した場合、詳細情報が表示されます:

```
[FAIL] Create posts table (12ms)

=== MIGRATION FAILED ===

Environment:
  Target: db1

SQL Content:
   1 | CREATE TABLE posts (
   2 |   id SERIAL PRIMARY KEY,
   3 |   title VARCHAR(200) NOT NULL
   4 | );

Error:
  relation "posts" already exists
```

### 環境固有の実行

```bash
# 本番環境のオーバーライドを読み込む
migraphe up --env production

# 開発環境のオーバーライドを読み込む
migraphe up --env development
```

## ロールバック（down）

`down` コマンドは、指定したバージョンまでマイグレーションをロールバックします。

### 基本的な使い方

```bash
# 指定バージョンに依存するマイグレーションをロールバック
migraphe down <version>

# 全てのマイグレーションをロールバック
migraphe down --all

# 確認プロンプトをスキップ
migraphe down -y <version>
migraphe down -y --all

# 実行計画のみ表示（実際には実行しない）
migraphe down --preview <version>
migraphe down --preview --all
```

### 動作の仕組み

#### バージョン指定の場合

`down <version>` コマンドは、指定したバージョン（ノード）**自身**と、それに**直接/間接的に依存する**マイグレーションをロールバックします。

**例:**
```
依存グラフ:
V001 <- V002 <- V003
  ↑
V004 (V001のみに依存)

migraphe down V002 実行:
✓ V003 をロールバック (V002に依存)
✓ V002 をロールバック (指定したバージョン)
✗ V004 はそのまま (V002に依存していない)
✗ V001 はそのまま (V002の依存先)
```

#### --all オプションの場合

`down --all` は、実行済みの**全て**のマイグレーションをロールバックします。依存関係の逆順で実行されるため、データの整合性が保たれます。

**例:**
```bash
$ migraphe down --all

The following migrations will be rolled back:
  - db1/003_create_comments: Create comments table
  - db1/002_create_posts: Create posts table
  - db1/001_create_users: Create users table

Rolling back all migrations.

Proceed with rollback? [y/N]: y

Rolling back...
  [DOWN] Create comments table ... OK (15ms)
  [DOWN] Create posts table ... OK (12ms)
  [DOWN] Create users table ... OK (10ms)

Rollback complete. 3 migrations rolled back.
```

### 実行フロー

```bash
$ migraphe down db1/001_create_users

The following migrations will be rolled back:
  - db1/003_create_comments: Create comments table
  - db1/002_create_posts: Create posts table
  - db1/001_create_users: Create users table

Rollback includes: db1/001_create_users (Create users table)

Proceed with rollback? [y/N]: y

Rolling back...
  [DOWN] Create comments table ... OK (15ms)
  [DOWN] Create posts table ... OK (12ms)
  [DOWN] Create users table ... OK (10ms)

Rollback complete. 3 migrations rolled back.
```

### dry-run オプション

実際にロールバックせずに、何が実行されるかを確認できます:

```bash
$ migraphe down --preview db1/001_create_users

[DRY RUN] The following migrations would be rolled back:
  - db1/003_create_comments: Create comments table
  - db1/002_create_posts: Create posts table
  - db1/001_create_users: Create users table

Rollback includes: db1/001_create_users (Create users table)

No changes made (dry run).
```

### 注意事項

1. **DOWNマイグレーションが必要**: ロールバックするには、タスクに `down` SQL が定義されている必要があります
2. **依存関係順で実行**: 依存されている側のマイグレーションから先にロールバックされます
3. **履歴に記録**: ロールバックも履歴テーブルに記録されます（direction: DOWN）
4. **実行済みのみ対象**: 履歴で実行済みとなっているマイグレーションのみがロールバック対象になります

## 設定の検証（validate）

`validate` コマンドは、設定ファイルをオフラインで検証します。データベース接続なしで全エラーを蓄積して一括表示します。

### 基本的な使い方

```bash
migraphe validate
```

### 検証項目

1. **プロジェクト設定**: `migraphe.yaml` の存在と妥当性
2. **ターゲット設定**: `targets/*.yaml` の必須フィールド（`type` など）
3. **タスク設定**: `tasks/**/*.yaml` の必須フィールド（`name`, `target`, `up` など）
4. **依存関係**: `dependencies` が存在するタスクIDを参照しているか
5. **グラフ構造**: 循環依存（サイクル）がないか

### 成功時の出力

```
Validation
==========

Checking project configuration... OK
Checking targets (2 files)... OK
Checking tasks (5 files)... OK
Checking dependencies... OK
Checking graph structure... OK

Validation successful.
```

### エラー時の出力

```
Validation
==========

Checking project configuration... OK
Checking targets (2 files)... FAIL
  × targets/test-db.yaml: Missing required property 'type'
Checking tasks (5 files)... FAIL
  × tasks/db1/create_users.yaml: Missing required property 'name'
  × tasks/db1/add_index.yaml: Target 'nonexistent' not found
Checking dependencies... FAIL
  × tasks/db1/add_index.yaml: Dependency 'db1/missing' not found
Checking graph structure... FAIL
  × Circular dependency detected: db1/a -> db1/b -> db1/a

Validation failed with 5 errors.
```

### 使用場面

- CI/CDパイプラインでのプレチェック
- プルリクエストの検証
- 設定ファイルのデバッグ
- 本番デプロイ前の確認

### 終了コード

| 終了コード | 意味 |
|-----------|------|
| 0 | 検証成功（エラーなし） |
| 1 | 検証失敗（1つ以上のエラー） |

## スキーマドキュメント生成（generate）

`generate` コマンドは、各種データソースからドキュメントやデータのエクスポートを生成します。ジェネレータシステムは**ソース/アウトプットプラグインアーキテクチャ**を採用しています。ソースプラグインがデータを抽出し、アウトプットプラグインが希望のフォーマットで出力します。同じデータソースを複数の形式で出力可能です。

### 設定

`migraphe.yaml` に `generators` セクションを追加します:

```yaml
project:
  name: my-project

history:
  target: history

generators:
  # スキーマドキュメントをMarkdownで出力
  - name: schema-docs
    type: jdbc-markdown
    source:
      type: jdbc-schema
      target: db1
    output-dir: docs/schema
    excludes:
      - schema: "information_schema"
      - schema: "public"
        table: "tmp_.*"

  # マイグレーションツリーをJSONで標準出力に出力
  - name: tree
    type: output-json
    source:
      type: migration-tree
    output-dir: docs
```

**フィールド:**
- `name`（必須）: ジェネレータの識別子
- `type`（必須）: アウトプットプラグインのタイプ（例: `jdbc-markdown`、`output-json`）
- `source`（ソース/アウトプットフローに必須）:
  - `type`: ソースプラグインのタイプ（例: `jdbc-schema`、`migration-tree`）
  - `target`（オプション）: データベース接続が必要なソースプラグイン用のターゲット名
- `output-dir`（オプション、デフォルト: `docs/schema`）: 生成ファイルの出力先ディレクトリ
- `excludes`（オプション）: 除外フィルタのリスト（正規表現パターン）
  - `schema`: スキーマ名にマッチする正規表現パターン
  - `table`: テーブル名にマッチする正規表現パターン（`schema` と組み合わせて使用）

### 利用可能なソースプラグイン

| プラグイン | タイプ | データ | 説明 |
|-----------|--------|--------|------|
| `migraphe-plugin-jdbc` | `jdbc-schema` | `JdbcSchemaInfo` | JDBC DatabaseMetaData経由でデータベーススキーマメタデータを抽出 |
| `migraphe-plugin-postgresql` | `postgresql-schema` | `PostgreSQLSchemaInfo` | JDBC基本スキーマ + PostgreSQL固有メタデータ（拡張機能、列挙型、シーケンス、関数、トリガー、マテリアライズドビュー、パーティション、ポリシー）をpg_catalogから抽出 |
| `migraphe-plugin-mysql` | `mysql-schema` | `MySQLSchemaInfo` | JDBC基本スキーマ + MySQL固有メタデータ（ストレージエンジン、テーブルメタ、トリガー、ルーチン、イベント、パーティション）をinformation_schemaから抽出 |
| （組み込み） | `migration-tree` | `MigrationGraphView` | マイグレーションDAG構造を提供 |

### 利用可能なアウトプットプラグイン

| プラグイン | タイプ | 説明 |
|-----------|--------|------|
| `migraphe-plugin-jdbc` | `jdbc-markdown` | `JdbcSchemaInfo` からMarkdownドキュメントを生成 |
| `migraphe-plugin-postgresql` | `postgresql-markdown` | PostgreSQL固有オブジェクト（拡張機能、列挙型、シーケンス、関数、トリガー、マテリアライズドビュー、パーティション、ポリシー）を含むMarkdownドキュメントを生成 |
| `migraphe-plugin-mysql` | `mysql-markdown` | MySQL固有オブジェクト（ストレージエンジン、テーブルメタデータ、トリガー、ルーチン、イベント、パーティション）を含むMarkdownドキュメントを生成 |
| `migraphe-plugin-generator-json` | `output-json` | 任意のデータを整形済みJSONで標準出力に出力 |

### 基本的な使い方

```bash
# 設定済みの全ジェネレータでドキュメントを生成
migraphe generate

# 特定のジェネレータのみ実行
migraphe generate --name mydb
```

### 出力構造（jdbc-markdown）

`jdbc-markdown` ジェネレータは以下のディレクトリ構造を生成します:

```
docs/schema/
└── mydb/
    └── public/
        ├── index.md              # スキーマ概要（テーブル/ビュー一覧）
        ├── tables/
        │   ├── users.md          # テーブル詳細（カラム、キー、インデックス）
        │   └── posts.md
        └── views/
            └── recent_posts.md   # ビュー詳細
```

各テーブルのドキュメントには以下が含まれます:
- カラム定義（名前、型、NULL許可、デフォルト値）
- 主キーとユニーク制約
- 外部キー参照（参照先テーブルへのクロスリンク付き）
- インデックス

#### 外部キーのレンダリング: Imported / Exported Keys

JDBC では外部キー関係に対して 2 つの視点が定義されており、ジェネレーターは各テーブルに対して両方をレンダリングします:

| `tables/<name>.md` 内のセクション | JDBC ソース | 意味 | リンク先 |
|---|---|---|---|
| **Foreign Keys** | `DatabaseMetaData.getImportedKeys()` | *このテーブル上の* FK カラム → 他テーブルの主キー | 参照先テーブル |
| **Referenced By** | `DatabaseMetaData.getExportedKeys()` | *他テーブル上の* FK カラム → このテーブルの主キー | 参照元（子）テーブル |

各行は 2 つの異なるカラムリストを使用します:

- `columns` — レンダリング対象テーブル側のローカルな FK カラム
- `referencedColumns` — リンク先テーブル側の主キーカラム

具体例として、`tables/users.md` をレンダリングする場合:

- **Foreign Keys** の `manager_id → users(id)` という行は、`users.manager_id` が `users(id)` を参照していることを意味する。
- **Referenced By** の `posts(user_id) → id` という行は、`posts.user_id` が `users.id` を参照していることを意味し、リンク先は `posts.md`（`users.md` ではない）。

この区別は最近修正された箇所です。以前のバージョンでは exported key のリンクが PK 側（参照先）テーブルを指していたため、`Referenced By` が自己参照的になり機能していませんでした。

### PostgreSQL固有ドキュメント

PostgreSQLデータベースの場合、`postgresql-markdown` アウトプットプラグインと `postgresql-schema` ソースプラグインを使用して、PostgreSQL固有オブジェクトを含む包括的なドキュメントを生成できます:

```yaml
generators:
  - name: mydb
    type: postgresql-markdown
    source:
      type: postgresql-schema
      target: db1
    output-dir: docs/schema
```

標準的なJDBCスキーマ情報（テーブル、ビュー、カラム、キー、インデックス）に加えて、生成されるドキュメントには以下が含まれます:
- **拡張機能**（例: `pgcrypto`、`uuid-ossp`） — `Owner` 列付き
- **列挙型** とその値 — `Owner` 列付き
- **シーケンス** と現在の値・パラメータ — `Owned By`（`pg_depend` による依存先 table.column）と `Owner`（ロール）の 2 列付き
- **関数** と引数型・戻り値型 — 個別ファイルに `Owner` プロパティ
- **トリガー** とタイミング、イベント、関連関数
- **マテリアライズドビュー** とカラム定義 — 個別ファイルに `Owner` プロパティ
- **パーティションテーブル** とパーティション戦略・キー
- **行レベルセキュリティ（RLS）ポリシー** とロール、コマンド、式

テーブル固有のファイルには、各テーブルに関連するトリガー、ポリシー、パーティション情報も含まれます。

**ロール所有者:** Tables/Views 一覧テーブルには `Owner` 列（`pg_get_userbyid(relowner)` から取得した PostgreSQL ロール名）が含まれ、各 `tables/<name>.md` / `views/<name>.md` ファイルのタイトル直下に `Owner: <role>` の行が出力されます。

### MySQL固有ドキュメント

MySQLデータベースの場合、`mysql-markdown` アウトプットプラグインと `mysql-schema` ソースプラグインを使用して、MySQL固有オブジェクトを含む包括的なドキュメントを生成できます:

```yaml
generators:
  mysql-docs:
    source:
      type: mysql-schema
      environment: db1
    output:
      type: mysql-markdown
    name: my-database
```

標準的なJDBCスキーマ情報（テーブル、ビュー、カラム、キー、インデックス）に加えて、生成されるドキュメントには以下が含まれます:
- **ストレージエンジン** MySQLインスタンスで利用可能なエンジン一覧
- **テーブルメタデータ** ENGINE、照合順序、行フォーマットを含む
- **トリガー** とタイミング、イベント、SQL文、`Definer`
- **ルーチン**（ストアドプロシージャおよび関数）とパラメータ・戻り値型、`Definer`
- **イベント** とスケジュール、ステータス、SQL本体、`Definer`
- **パーティションテーブル** とパーティション方式、式、パーティション詳細

テーブル固有のファイルには、各テーブルに関連するトリガーおよびパーティション情報も含まれます。

**`DEFINER` の表示:** Views 一覧テーブルには `Definer` 列（`information_schema.VIEWS.DEFINER` から取得）が含まれ、各 `views/<name>.md` ファイルのタイトル直下に `Definer: <user>` の行が出力されます。Triggers / Routines / Events も同様に各一覧テーブル・個別ファイルで DEFINER を表示します。MySQL のテーブル自体には DEFINER がないため、Tables 一覧は変更されません。

**注意:** MySQL JDBCはデータベースをカタログとして返します（スキーマではありません）。`mysql-schema` ソースプラグインは `connection.getCatalog()` を使用したカタログベースのスキーマ検出を行います。

### 除外フィルタリング

`excludes` を使用して、正規表現パターンにマッチするスキーマやテーブルをスキップします:

```yaml
generators:
  - name: mydb
    type: jdbc-markdown
    source:
      type: jdbc-schema
      target: db1
    output-dir: docs/schema
    excludes:
      - schema: "information_schema"     # スキーマ全体を除外
      - schema: "pg_catalog"             # PostgreSQLシステムスキーマを除外
      - schema: "public"
        table: "tmp_.*"                  # publicスキーマの一時テーブルを除外
      - schema: ".*"
        table: "flyway_schema_history"   # 全スキーマで特定テーブルを除外
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

migraphe up --env production
```

## 高度な機能

### 並列実行

MigrapheはJava Virtual Threadsを使用したオプトインの並列実行をサポートしています。有効にすると、依存関係がすべて完了したノードが同時に実行されます。

**設定（`migraphe.yaml`）:**

```yaml
project:
  name: my-project

history:
  target: history

execution:
  parallel: true        # 並列実行を有効化（デフォルト: false）
  max-parallelism: 4    # 同時実行タスク数の上限（0 = 無制限、デフォルト: 0）
```

- `execution.parallel`: `true`に設定すると並列実行が有効になります。`false`（デフォルト）の場合、マイグレーションはトポロジカル順に逐次実行されます。
- `execution.max-parallelism`: 同時に実行するタスク数を制限します。`0`（デフォルト）で無制限になります。

**動作の仕組み:**

同じ依存レベルのノードがVirtual Threadsを使用して並列実行されます。Ready-basedアプローチにより、ノードのすべての依存関係が満たされ次第、実行対象になります。いずれかのタスクが失敗すると、フェイルファスト動作により新しいタスクの投入が即座に停止されます。

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

## Gradleプラグイン

Migrapheはマイグレーションをビルドプロセスに統合するためのGradleプラグインを提供します。

### セットアップ

`settings.gradle.kts` にプラグイン解決の設定を追加:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.kakusuke.migraphe") {
                useModule("com.github.kakusuke.migraphe:migraphe-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

`build.gradle.kts` に追加:

```kotlin
plugins {
    id("io.github.kakusuke.migraphe") version "v0.2.0"
}

migraphe {
    baseDir.set(layout.projectDirectory.dir("db")) // デフォルト: プロジェクトディレクトリ
}

dependencies {
    // 使用するデータベースに応じてプラグインを選択:
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.2.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.2.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.2.0")
}
```

### 利用可能なタスク

| タスク | 説明 |
|--------|------|
| `migrapheValidate` | 設定ファイルの検証（オフライン、DB接続不要） |
| `migrapheStatus` | マイグレーション実行状況の表示 |
| `migrapheUp` | マイグレーション（前進）の実行 |
| `migrapheDown` | ロールバック（後退）の実行 |
| `migrapheGenerate` | スキーマドキュメントの生成 |

### タスクオプション

**migrapheUp**:
- `--target=<nodeId>` — 特定のノードまでマイグレーション
- `--preview` — 実行せずにプレビュー

**migrapheDown**:
- `--target=<nodeId>` — 特定のノードまでロールバック
- `--all` — 全実行済みマイグレーションのロールバック
- `--preview` — 実行せずにプレビュー

**migrapheGenerate**:
- `--name=<name>` — 特定のジェネレータのみ実行

プロジェクトプロパティ（`-P`）でも指定可能:

```bash
./gradlew migrapheUp -Pmigraphe.up.target=db1/create_users
./gradlew migrapheDown -Pmigraphe.down.all=true
```

## トラブルシューティング

### よくある問題

#### 1. "No plugin found for type" エラー

**問題:**
```
No plugin found for type 'postgresql'.
No plugins are currently loaded.
```

**解決策:**
- `migraphe.yaml` の `plugins` セクションにプラグインの Maven 座標を追加
- `migraphe pin` でロックファイルを (再) 生成
- または `plugins/` ディレクトリにプラグイン JAR ファイルを配置
- [プラグインのインストール](#プラグインのインストール) セクションを参照

#### 1b. "Failed to resolve plugin" エラー

**問題:**
```
Failed to resolve plugin: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.2.0
```

**解決策:**
- `migraphe.yaml` の Maven 座標と `repository:` 指定が正しいか確認
- JitPack 側で `v0.2.0` のビルドが成功しているかを <https://jitpack.io/#kakusuke/migraphe> で確認
- JitPack および Maven Central へのネットワーク接続を確認
- `migraphe pin` でロックファイルを再生成

#### 2. "Target not found" エラー

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
   migraphe status --verbose
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

## 配布チャネルロードマップ

Migraphe アーティファクトは以下のチャネルで提供されます:

| チャネル | 状態 | groupId | 対象 |
|----------|------|---------|------|
| GitHub Releases (fat JAR) | ✅ 提供中 | — | CLI バイナリ |
| JitPack | ✅ 提供中 | `com.github.kakusuke.migraphe` | プラグイン JAR + Gradle プラグイン |
| Maven Central | 📅 公開予定 | `io.github.kakusuke.migraphe` | プラグイン JAR + Gradle プラグイン |

プラグイン JAR および Gradle プラグインは現在 JitPack 経由で `com.github.kakusuke.migraphe:<module>:v0.2.0` として配布されています。Maven Central への公開は予定中で、その際に groupId が `io.github.kakusuke.migraphe` に切り替わります。

## 次のステップ

- 設計の詳細については[アーキテクチャドキュメント](../CLAUDE.md)を参照
- 翻訳については[英語版ユーザーガイド](USER_GUIDE.md)を確認
- `examples/`ディレクトリのサンプルプロジェクトを確認（利用可能な場合）

## サポート

問題や質問については:
- GitHub Issues: https://github.com/kakusuke/migraphe/issues
- ドキュメント: https://github.com/kakusuke/migraphe/tree/main/docs
