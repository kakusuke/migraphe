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
8. [定義を適用済みとして記録（amend）](#定義を適用済みとして記録amend)
9. [設定の検証（validate）](#設定の検証validate)
10. [スキーマドキュメント生成（generate）](#スキーマドキュメント生成generate)
11. [環境管理](#環境管理)
12. [高度な機能](#高度な機能)
13. [Gradleプラグイン](#gradleプラグイン)
14. [トラブルシューティング](#トラブルシューティング)

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
- サポート対象のデータベース（PostgreSQL、MySQL、MariaDB、または任意のJDBC対応データベース）。
  PostgreSQL、MySQL 8.0、MariaDB 10.1 で検証済み
  - **MySQL** 5.6.4 以降: 履歴テーブルが `TIMESTAMP(6)` を使うため、これを解釈できない
    Oracle MySQL 5.5 は非対応
  - **MariaDB** 5.5 系以降: 履歴テーブルのインデックスキー長を InnoDB の 767 バイト制限内に
    収めているため、`innodb_large_prefix` の無いサーバでも作成できます

### mise でインストール（推奨）

リリース tarball は `bin/` と `lib/` をルート直下に同梱しているため、mise の GitHub バックエンドが追加オプション無しで取り込めます:

```bash
mise use github:kakusuke/migraphe
```

### リリース成果物のダウンロード

```bash
# tar.gz — Linux / macOS（bin/ と lib/ を展開先ディレクトリに展開）
mkdir -p ~/.local/migraphe
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0.tar.gz | tar xz -C ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"

# zip — Windows
curl -L -o migraphe.zip https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0.zip
unzip migraphe.zip -d ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"

# fat JAR — 単一ファイル
curl -L -o migraphe.jar https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0-all.jar
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
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.ja.md) | `postgresql` | PostgreSQL データベースサポート（`postgresql-schema` ソースおよび `postgresql-markdown` アウトプットプラグインを含む） |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.ja.md) | `mysql` | MySQL 8.0+ データベースサポート（`mysql-schema` ソースおよび `mysql-markdown` アウトプットプラグインを含む） |
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.ja.md) | `jdbc` | 汎用 JDBC サポート（任意の JDBC データベースで使用可能） |
| [`migraphe-plugin-generator-json`](../migraphe-plugin-generator-json/README.ja.md) | `output-json` | JSON 出力ジェネレータプラグイン |

各プラグインの `README.ja.md` には、ターゲットのフィールド、接続例、データベース固有の挙動が網羅されています。詳細は上記のプラグイン名のリンクを参照してください。

#### 方法1: Maven 座標（推奨）

`migraphe.yaml` に `plugins` セクションを追加し、Maven 座標を記述します。Migraphe のプラグインは JitPack 経由で配布されているため、JitPack リポジトリを宣言したうえで map 形式で参照します:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
    repository: jitpack
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.6.0
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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
    repository: jitpack

project:
  name: my-project

history:
  target: history  # 実行履歴を保存するターゲット名
```

**フィールド:**
- `plugins`（任意）: CLI プラグイン解決用の Maven 座標リスト（`groupId:artifactId:version`）
- `project.name`（必須）: プロジェクト識別子
- `project.scan-root`（任意）: `tasks/`、`targets/`、`environments/`、`plugins/` を探索する起点ディレクトリ。`migraphe.yaml` の親ディレクトリ起点の相対パス、または絶対パスを指定できます。未指定の場合は `migraphe.yaml` の親ディレクトリと同じ（既定値）。CLI と Gradle プラグインのどちらでも同じフィールドを参照するため挙動が一致します。
- `history.target`（必須）: マイグレーション履歴を保存するターゲット名

**例: `scan-root` でマイグレーション資材をサブディレクトリにまとめる**

```yaml
project:
  name: my-app
  scan-root: config
history:
  target: main
```

この設定では、Migraphe は `migraphe.yaml` の親ディレクトリを基準として `config/tasks/` からタスクを、`config/targets/` からターゲットを、`config/environments/` から環境設定を、レガシーの `plugins/` ディレクトリも `config/plugins/` から読み込みます。

### ターゲット設定

ターゲットファイルはデータベース接続を定義します。`targets/`ディレクトリに配置します。

**例: `targets/db1.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

**共通フィールド:** すべてのターゲットには `type`（背後のプラグイン）と、そのプラグインの接続設定（通常は `jdbc_url`、`username`、`password`）が必要です。**正確なフィールドセットは各プラグインが定義します** — 例えば汎用 `jdbc` タイプは追加で `driver_class` を必要とします。完全なフィールド一覧（必須/任意・デフォルト）とデータベース別の例は各プラグインの README を参照してください:

| プラグイン | タイプ | ターゲットフィールドと例 |
|-----------|--------|------------------------|
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.ja.md) | `postgresql` | PostgreSQL 接続フィールド |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.ja.md) | `mysql` | MySQL 接続フィールド |
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.ja.md) | `jdbc` | 汎用 JDBC フィールド（`driver_class`、`db_label` を含む） |

注: ターゲット名はファイル名から導出されます（例: `db1.yaml` → ターゲット名 `db1`）。

**例: `targets/history.yaml`**（履歴ストアとして使用）

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
- `autocommit`（オプション）: トランザクションなしで実行（[Autocommitモード](#autocommitモード)を参照）

### 環境固有の設定

環境ファイルは、特定の環境用にベース設定をオーバーライドします。

**例: `environments/production.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://prod-db.example.com:5432/mydb
    password: ${env.DB_PASSWORD}  # OS環境変数
```

`${VAR}`を使用した変数置換はMicroProfile Configによりサポートされています。値は次の優先順位（高い順）で解決されます: Gradle注入のvariables、`environments/*.yaml`プロファイル、システムプロパティ（`-D`）、`migraphe.yaml`/`targets`/`tasks`。**OS環境変数は `env.` 接頭辞付きで参照する必要があります（`${VAR}` ではなく `${env.VAR}`）** — これにより環境変数が `target.*` などの設定キーに混入しません。インライン既定値も利用できます: `${env.VAR:default}`。

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

単一の `up` / `down` には `;` 区切りで複数の文を記述できます。Migraphe は**ターゲットの SQL 方言**を使ってスクリプトを分割し、デフォルトのトランザクションモードでも順次実行します（複数文の実行に autocommit は**不要**です）。PostgreSQL のドル引用符（`$$ ... $$`）や MySQL の `BEGIN ... END` ブロック／`DELIMITER` ディレクティブといった方言固有の構文を認識するため、本体内の `;` でルーチン本体が分割されることはありません。

```yaml
name: Add indexes
target: db1
dependencies:
  - db1/001_create_users
up: |
  CREATE INDEX idx_users_email ON users(email);
  CREATE INDEX idx_users_created_at ON users(created_at);
down: |
  DROP INDEX IF EXISTS idx_users_email;
  DROP INDEX IF EXISTS idx_users_created_at;
```

方言ごとのルールと、ストアドプロシージャ／関数本体の例（PostgreSQL のドル引用符、MySQL の `BEGIN ... END` / `DELIMITER`、汎用 `;` 分割）は各プラグインの README に記載しています:

- PostgreSQL: [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.ja.md)
- MySQL: [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.ja.md)
- 汎用 JDBC: [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.ja.md)

### マイグレーション中のコメント

SQL コメントは除去されず保持されます。先頭のコメントは直後の文に付随したまま残り、行コメント（`--`、MySQL の `#`）は末尾の改行も保持されるため、次の文が誤ってコメントアウトされることはありません。方言固有の*実行される*コメントも尊重されます。MySQL のバージョン条件付きコメント（`/*! ... */`、`/*!50110 ... */`）はサーバに送られて実行され、オプティマイザヒント（`/*+ ... */`）も文に保持されます。除外されるのは空または空白のみのセグメントだけで、コメントのみの行は無害な no-op として扱われます。

### Autocommitモード

> 複数文の実行に autocommit は**不要**です。デフォルトのトランザクションモードで分割して順次実行されます。autocommit は、トランザクション内で実行できない文（`CREATE DATABASE`、`CREATE INDEX CONCURRENTLY` など）専用です。

一部のSQL文はトランザクション内で実行できません。そのような場合はタスクに `autocommit: true` を指定します。各文は単一トランザクションでまとめられず、即座にコミットされます:

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

どの文が autocommit を必要とするかはデータベース依存です（例: PostgreSQL の `CREATE INDEX CONCURRENTLY`、`VACUUM`、`CLUSTER`）。方言固有のユースケースは各プラグインの README を参照してください: [postgresql](../migraphe-plugin-postgresql/README.ja.md)、[mysql](../migraphe-plugin-mysql/README.ja.md)、[jdbc](../migraphe-plugin-jdbc/README.ja.md)。

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

**マーカー:**

| マーカー | 意味 |
|---------|------|
| `[ ]` | 未適用 |
| `[✓]` | 適用済みで、`up` の内容に変化が検出されていない。プラグインが fingerprint を提供せず判定の対象外である場合もこれ |
| `[!]` | 適用済みだが、`up` の内容が適用後に編集されている。ロールバックして再適用するか、[`migraphe amend`](#定義を適用済みとして記録amend) で解消する |
| `[?]` | プラグインは fingerprint を提供するが、適用時の行に記録が無いため変化を判定できない。0.7.0 より前に書かれた行がこれになる。解消できるのは [`migraphe amend`](#定義を適用済みとして記録amend) だけ |
| `[E]` | プラグインの fingerprint が読めなかった（プラグイン側の不具合） |

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
| `--preview` | 実行計画のみ表示し、実際には実行しない（`--dry-run` も同義として受け付ける） |

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

`--env <name>` を渡すと、`environments/<name>.yaml` を `targets/` 設定の上にオーバーレイします。このオーバーレイは最優先で適用され、target の接続設定(`jdbc_url`、`username`、`password` など)を上書きします。`up`、`down`、`status` コマンドで利用できます:

```bash
# environments/production.yaml の上書きを適用
migraphe up --env production
migraphe status --env production

# environments/development.yaml の上書きを適用
migraphe up --env development
```

`environments/<name>.yaml` が存在しない場合、このフラグは無視されます(ベース設定が使われます)。`validate` と `generate` は現時点では `--env` を読み取りません。

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

## 定義を適用済みとして記録（amend）

`amend` コマンドは、**履歴**を現在のタスクファイルに合わせて書き換えます。記録されている fingerprint が無いか、定義と食い違っているマイグレーションについて、現在の fingerprint を記録します。**DB のオブジェクトには一切触れません。**

`status` が `[?]` または `[!]` を表示していて、かつ「DB の現在の状態は正しい」と判断した場合に使います。

- `[?]` — 0.7.0 より前のバージョンで適用され、fingerprint が記録されていない。これを解消できるのは `amend` だけです（`up` は適用済みのマイグレーションを飛ばすため、後から fingerprint を埋めることはありません）。
- `[!]` — 適用後に `up` の内容を編集したが、その編集にロールバックは不要（コメントの追加、フォーマッタの実行、既に手で直してあるスキーマなど）。

逆に、間違っているのが *DB* 側であれば、`amend` ではなくロールバックして再適用してください（`migraphe down <id>` のあと `migraphe up`）。`amend` はその処理はしません。

### 基本的な使い方

```bash
$ migraphe amend

Amend plan (history only — no database changes):

  [?] → [✓]  db1/001_create_users - Create users table
  [!] → [✓]  db1/002_create_posts - Create posts table
             ⚠ edited after it was applied; what actually ran will no longer be recorded

2 fingerprints will be recorded.

Record 2 fingerprints? [y/N]: y

Recorded 2 fingerprints.
```

まずプレビュー（確認プロンプトも書き込みも無し）:

```bash
migraphe amend --preview
```

差分が無い場合は `Nothing to amend.` を表示して終了コード 0 で終わります。

### コマンドオプション

| オプション | 説明 |
|-----------|------|
| `--preview` | 何も記録せずに計画のみ表示（旧名 `--dry-run` も受け付けます） |
| `-y` | 確認プロンプトをスキップ |
| `--env <name>` | `environments/<name>.yaml` のオーバーレイを適用 |

**マイグレーションの指定も `--all` もありません**（意図的）。対象は常に「差分が出ている全マイグレーション」で、このコマンドが持つスコープはそれ一つだけです。

### 重要な注意点

1. **元の fingerprint は失われます**: `amend` は列を上書きし、以前の値をどこにも残しません。実行後は「定義が食い違っていた」という事実を復元できません。これは意図した設計です（[ARCHITECTURE.md](ARCHITECTURE.md) 参照）。
2. **変わるのは fingerprint だけ**: `executed_at`、実行時間、保存されたロールバック SQL はそのまま残るため、`status` は実際に適用された時刻を表示し続けます。
3. **`[!]` は証拠を捨てる操作です**: 実際に実行された内容の fingerprint が、いまファイルに書かれている内容の fingerprint に置き換わります。その編集を DB に反映する必要があるなら、ロールバックしてください。計画表示でこの行に警告が付くのはそのためです。
4. **成功した UP の行のみが対象**: 最新の履歴行がロールバックや失敗であるマイグレーションは対象外です。
5. **履歴リポジトリ側の対応が必要**: 同梱の JDBC / PostgreSQL / MySQL / インメモリの各リポジトリは対応済みです。書き換え機能（capability）を実装していないサードパーティ製リポジトリの場合、`amend` は成功したふりをせずエラーで停止します。

### 終了コード

| 終了コード | 意味 |
|-----------|------|
| 0 | 計画した分すべてを記録できた（対象が無かった場合、プレビューの場合、プロンプトで `N` と答えた場合も 0） |
| 1 | 計画した行を記録できなかった（書き込み時点で行が消えていた）、またはコマンドが失敗した |

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
- `name`（必須）: ジェネレータの識別子。`migraphe generate --name` で指定します。Markdown アウトプットプラグインではドキュメントのタイトルも兼ね、`index.md` の見出しとして `# <name>` の形で出力されます。出力パスの一部にはなりません。
- `type`（必須）: アウトプットプラグインのタイプ（例: `jdbc-markdown`、`output-json`）
- `source`（ソース/アウトプットフローに必須）:
  - `type`: ソースプラグインのタイプ（例: `jdbc-schema`、`migration-tree`）
  - `target`（オプション）: データベース接続が必要なソースプラグイン用のターゲット名
- `output-dir`（オプション、デフォルト: `docs/schema`）: 生成ファイルの出力先ディレクトリ
- `er-diagram`（オプション、デフォルト: `true`）: Markdown アウトプットプラグインで、`index.md` に Mermaid ER 図を埋め込みます。これは **すべての** ER 図出力のマスタスイッチです。`false` にすると、`er-diagram-per-table` の設定にかかわらず `index.md` の ER 図も各テーブルページの ER 図も出力されません。
- `er-diagram-keys-only`（オプション、デフォルト: `false`）: `true` にすると、ER 図の各エンティティは主キー・外部キーのカラムのみを表示します（リレーションには影響しません）。デフォルト `false` は全カラムを表示します。
- `er-diagram-layout`（オプション、デフォルト: `elk`）: 生成される `erDiagram` フェンス冒頭に YAML frontmatter を出力して指定する Mermaid のレイアウトエンジンです。Mermaid 公式のレイアウト名は `elk` / `dagre` / `tidy-tree` / `cose-bilkent` です。`[A-Za-z0-9_-]+` にマッチする値のみが有効で、それ以外の文字を含む値の場合は frontmatter を出力せず、従来どおりフェンスは `erDiagram` から始まります。無効化したい場合は、この文字集合の外の値（例: `er-diagram-layout: " "`）を指定してください。値を空にする（`er-diagram-layout:`）のは無効化ではなく設定エラーになります。
- `er-diagram-per-table`（オプション、デフォルト: `true`）: `true` にすると、各テーブルページにもそのテーブルを中心とした近傍 ER 図の `## ER Diagram` セクション（ページヘッダ直後・`## Columns` の前）を出力します。`false` にすると ER 図は `index.md` のみになります。
- `er-diagram-per-table-max-entities`（オプション、デフォルト: `60`）: テーブルページの近傍 ER 図に含められるエンティティ数の上限です。近傍がこの上限を超えるテーブルページでは、図の代わりに省略メッセージと `index.md` の全体 ER 図へのリンクを出力します。`0` 以下を指定すると無制限です。ちょうど上限のときは図が出力されます（超過時のみ省略）。
- `excludes`（オプション）: 除外フィルタのリスト（正規表現パターン）
  - `schema`: スキーマ名にマッチする正規表現パターン
  - `table`: テーブル名にマッチする正規表現パターン（`schema` と組み合わせて使用）

利用可能な source/output タイプと、それぞれの**タイプ別の完全なオプション表**は各プラグインの README（下記リンク）に記載しています。

### 利用可能なソースプラグイン

| プラグイン | タイプ | データ | 説明 |
|-----------|--------|--------|------|
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.ja.md) | `jdbc-schema` | `JdbcSchemaInfo` | JDBC DatabaseMetaData経由でデータベーススキーマメタデータを抽出 |
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.ja.md) | `postgresql-schema` | `PostgreSQLSchemaInfo` | JDBC基本スキーマ + PostgreSQL固有メタデータ（拡張機能、列挙型、シーケンス、関数、トリガー、マテリアライズドビュー、パーティション、ポリシー）をpg_catalogから抽出 |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.ja.md) | `mysql-schema` | `MySQLSchemaInfo` | JDBC基本スキーマ + MySQL固有メタデータ（ストレージエンジン、テーブルメタ、トリガー、ルーチン、イベント、パーティション）をinformation_schemaから抽出 |
| （組み込み） | `migration-tree` | `MigrationGraphView` | マイグレーションDAG構造を提供 |

### 利用可能なアウトプットプラグイン

| プラグイン | タイプ | 説明 |
|-----------|--------|------|
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.ja.md) | `jdbc-markdown` | `JdbcSchemaInfo` からMarkdownドキュメントを生成 |
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.ja.md) | `postgresql-markdown` | PostgreSQL固有オブジェクト（拡張機能、列挙型、シーケンス、関数、トリガー、マテリアライズドビュー、パーティション、ポリシー）を含むMarkdownドキュメントを生成 |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.ja.md) | `mysql-markdown` | MySQL固有オブジェクト（ストレージエンジン、テーブルメタデータ、トリガー、ルーチン、イベント、パーティション）を含むMarkdownドキュメントを生成 |
| [`migraphe-plugin-generator-json`](../migraphe-plugin-generator-json/README.ja.md) | `output-json` | 任意のデータを整形済みJSONで標準出力に出力 |

### 基本的な使い方

```bash
# 設定済みの全ジェネレータでドキュメントを生成
migraphe generate

# 特定のジェネレータのみ実行
migraphe generate --name mydb
```

### 出力構造

Markdown アウトプットプラグイン（`jdbc-markdown`、`postgresql-markdown`、`mysql-markdown`）は、`output-dir` 直下にデータベース全体の `index.md` を 1 つ書き出し、加えてスキーマごとに 1 ディレクトリ（`<output-dir>/<schema>/`）を作成してその中に `tables/` と `views/` ディレクトリを生成します。各テーブルページには、カラム定義（名前、型、NULL 許可、デフォルト値）、主キー/ユニークキー、相互リンク付きの外部キー（**Foreign Keys**（imported key）と **Referenced By**（exported key）の両視点）、インデックスが含まれます。正確なディレクトリ構造と imported/exported 外部キーのレンダリングは [`migraphe-plugin-jdbc` の README](../migraphe-plugin-jdbc/README.ja.md) に記載しています。

デフォルトでは、`index.md` にはデータベース全体の **ER 図** も Mermaid の `erDiagram` 記法（```mermaid コードフェンス。GitHub や多くの Markdown ビューアがインラインでレンダリング）で 1 枚埋め込まれます。各テーブルはカラム（型と PK/FK 印付き。主キーかつ外部キーのカラムは `PK, FK` と併記）を持つエンティティとなり、外部キーはリレーション（`||--o{`）として描かれます。図はスキーマを考慮します。別スキーマの同名テーブルもそれぞれ別エンティティになり、スキーマをまたぐ外部キーも描画され、テーブルページ内のスキーマ跨ぎのリンクは参照先スキーマのディレクトリへ解決されます。カラムの型は基底の型名で表示されます（例: PostgreSQL の列挙型はスキーマ修飾・引用符付きではなく `user_account_status` と表示）。図は 1 枚に統合されます。Mermaid の `erDiagram` にはグルーピング構文が無いため、スキーマ単位でテーブルを枠囲いすることはしません。ジェネレータに `er-diagram: false` を指定するとこのセクションを抑制でき、`er-diagram-keys-only: true` を指定すると各エンティティを主キー・外部キーのカラムのみに絞って図をコンパクトにできます。

#### テーブルごとの ER 図

デフォルト（`er-diagram-per-table: true`）では、各テーブルページにもそのページ専用の `## ER Diagram` セクションが、ページヘッダ直後・`## Columns` の前に出力されます。ここに描かれるのはデータベース全体ではなく、そのテーブルの **近傍** だけです。すなわち、テーブル自身と、外部キーを参照先方向に辿って推移的に到達できるすべてのテーブル（祖先、さらにその祖先……）、および自身を推移的に参照しているすべてのテーブル（子孫、さらにその子孫……）です。リレーションは、両端が近傍集合に含まれる外部キーすべてについて描画されます。

近傍は意図的に「無向グラフの連結成分」ではありません。兄弟方向、つまり「祖先の別の子孫」や「子孫の別の祖先」までは辿らないため、リンクが密なスキーマでも図が焦点を保ちます。循環参照・自己参照・スキーマをまたぐ外部キーはいずれも扱えます。また `excludes` で除外されたテーブルは経路を切断するため、近傍が除外テーブルを経由して広がることはありません。

近傍のエンティティ数が `er-diagram-per-table-max-entities`（デフォルト `60`）を超えると、図の代わりに省略メッセージと全体 ER 図へのリンクが出力されます:

```markdown
## ER Diagram

ER diagram omitted: this table's neighborhood includes 82 entities, exceeding the configured limit of 60. See the full [ER diagram](../../index.md) in the database index instead.
```

#### ER 図のレンダリングに関する注意

デフォルトの `er-diagram-layout: elk` では、生成される各 ER 図のフェンス冒頭にレイアウトエンジンを指定する Mermaid の frontmatter が出力されます:

````markdown
## ER Diagram

```mermaid
---
config:
  layout: elk
---
erDiagram
  ...
```
````

- **レイアウトの frontmatter は Mermaid 9.4 以降が必要です。** それ未満のレンダラでは冒頭の `---` ブロックが図の一部として解釈され、構文エラーになる可能性があります。Mermaid 9.4 より古いレンダラを使う場合は、`er-diagram-layout` に `[A-Za-z0-9_-]` 以外の文字を含む値（例: `er-diagram-layout: " "`）を指定してください。そうするとフェンスは `erDiagram` から始まり、このオプションが存在しなかった頃とまったく同じ出力になります。
- **GitHub は `@mermaid-js/layout-elk` を登録していないため、`layout: elk` は dagre にフォールバックします。** GitHub 上でも図が壊れることはありませんが、ELK によるレイアウト改善の効果は得られません。`elk` が有効になるのは ELK プラグインを読み込んでいるレンダラ、たとえば [mermaid.live](https://mermaid.live) や ELK レイアウトパッケージを設定した VitePress サイトなどです。
- **デフォルトの上限 `60` エンティティは文字数の代理指標であり、厳密な文字数上限ではありません。** エンティティ数はレンダリングサイズの近似にすぎません。1 エンティティのコストはおよそ 45 文字 + 1 カラムあたり約 40 文字なので、8〜10 カラムのテーブルであれば 60 エンティティで 22,000〜28,000 文字程度、GitHub の Mermaid 図 1 枚あたり約 50,000 文字という制限に対して十分余裕があります。一方、20 カラムを超える幅広テーブルが多いスキーマでは、60 エンティティでも 50,000 文字を超えることがあります。図が切り詰められたり拒否されたりする場合は、`er-diagram-per-table-max-entities` の値を下げる、あるいは各エンティティを PK/FK カラムのみに絞る `er-diagram-keys-only: true` を併用してください。
- **空値や非数値の YAML は即座にエラーになります。** `er-diagram-per-table-max-entities:` と値を書かない場合や、`er-diagram-per-table-max-entities: abc` のような非数値を書いた場合、SmallRye が設定ロード時に例外（`SRCFG00040` / `SRCFG00039`）を投げます（デフォルト値へ暗黙にフォールバックはしません）。`er-diagram-layout:` を空にした場合も同様です。これは `execution.max-parallelism` など他のオプションと同じ挙動です。

### データベース固有のドキュメント

PostgreSQL および MySQL プラグインは専用のソース／アウトプットの組み合わせを提供しており、標準的な JDBC スキーマ（テーブル、ビュー、カラム、キー、インデックス）を超えたデータベース固有のオブジェクトで生成 Markdown を拡充します。

たとえば PostgreSQL の組み合わせ（`postgresql-schema` ソース + `postgresql-markdown` アウトプット）は拡張機能、列挙型、シーケンス、関数、トリガー、マテリアライズドビュー、パーティション、RLS ポリシーを追加し、MySQL の組み合わせ（`mysql-schema` + `mysql-markdown`）はストレージエンジン、テーブルメタデータ、トリガー、ルーチン、イベント、パーティションを追加します。

```yaml
generators:
  - name: mydb
    type: postgresql-markdown
    source:
      type: postgresql-schema
      target: db1
    output-dir: docs/schema
    er-diagram: false            # オプション。省略または true で Mermaid ER 図を埋め込む
    # er-diagram-keys-only: true # オプション。ER 図に主キー・外部キーのカラムのみ表示
    # er-diagram-layout: elk     # オプション。Mermaid のレイアウトエンジン（elk, dagre, tidy-tree, cose-bilkent）
    # er-diagram-per-table: true # オプション。各テーブルページにも近傍 ER 図を出力
    # er-diagram-per-table-max-entities: 60 # オプション。この値を超える近傍ではテーブルページの ER 図を省略（0 以下で無制限）
```

データベース固有オブジェクトの完全な一覧、所有者／DEFINER の表示、テーブルごとに含まれる内容については、各プラグインの README に記載されています:

- PostgreSQL: [`migraphe-plugin-postgresql/README.ja.md`](../migraphe-plugin-postgresql/README.ja.md)
- MySQL: [`migraphe-plugin-mysql/README.ja.md`](../migraphe-plugin-mysql/README.ja.md)

### 除外フィルタリング

Markdown ジェネレーターは `excludes` リストを受け付け、正規表現（`schema` / `table` パターン）にマッチするスキーマやテーブルをスキップします。完全なオプションリファレンスと例は各プラグインのジェネレーターフィールドの節を参照してください: [postgresql](../migraphe-plugin-postgresql/README.ja.md)、[mysql](../migraphe-plugin-mysql/README.ja.md)、[jdbc](../migraphe-plugin-jdbc/README.ja.md)。

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
    password: ${env.PROD_DB_PASSWORD}  # OS環境変数から

  history:
    jdbc_url: jdbc:postgresql://prod-db.company.com:5432/migraphe_history
    password: ${env.PROD_HISTORY_PASSWORD}
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

同じ依存レベルのノードがVirtual Threadsを使用して並列実行されます。Ready-basedアプローチにより、ノードのすべての依存関係が満たされ次第、実行対象になります。

**失敗時の挙動 (fail-soft):** いずれかのタスクが失敗しても、その失敗ノードに（推移的に）依存しないタスクは引き続き実行されます。失敗ノードに依存する後続タスクは `dependency failed: <id>` の理由でスキップ通知されます。すべての実行可能なタスクが完了したのち、失敗があれば全体の結果が `failure` として返されます。これは UP / DOWN / 並列 / 直列のすべての実行モードに共通の挙動です。

この設計により、「失敗してから rerun した場合に流れるタスクの集合」が「最初から成功して流れた場合に流れるタスクの集合」と一致するため、再実行の冪等性が保たれます。

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
- `id`: 一意の実行ID（時刻順に並ぶ UUIDv7。生成順と辞書順が一致する）
- `node_id`: タスクID
- `target_id`: ターゲット名（タスクの `target:` が指す `targets/` の定義）。0.6.0 より前は
  `environment_id` という列名だったが、`initialize()` がその場でリネームする。`--env` で選ぶ
  オーバーレイ名が入ったことは一度もない（あれは設定値を上書きするだけ）
- `direction`: UPまたはDOWN
- `status`: SUCCESS、FAILURE、またはSKIPPED
- `description`: タスク名
- `executed_at`: 実行タイムスタンプ
- `duration_ms`: 実行時間
- `serialized_down_task`: ロールバックSQL（UPマイグレーションのみ）
- `error_message`: エラーの詳細（FAILUREステータスのみ）
- `fingerprint`: 適用したUPの内容のフィンガープリント。UP成功時のみ記録される。JDBC / PostgreSQL /
  MySQL プラグインは `up:` のSQLを前後の空白を除いて SHA-256 でハッシュした16進文字列を使う。DOWNの行、
  0.7.0 より前に書かれた行、プラグインが提供しない場合は空になり、いずれも「変更なし」ではなく
  「不明」を意味する

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
    id("io.github.kakusuke.migraphe") version "v0.6.0"
}

migraphe {
    baseDir.set(layout.projectDirectory.dir("db")) // デフォルト: プロジェクトディレクトリ
}

dependencies {
    // 使用するデータベースに応じてプラグインを選択:
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.6.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.6.0")
}
```

### 利用可能なタスク

| タスク | 説明 |
|--------|------|
| `migrapheValidate` | 設定ファイルの検証（オフライン、DB接続不要） |
| `migrapheStatus` | マイグレーション実行状況の表示 |
| `migrapheUp` | マイグレーション（前進）の実行 |
| `migrapheDown` | ロールバック（後退）の実行 |
| `migrapheAmend` | 現在の定義を適用済みとして記録（履歴のみ） |
| `migrapheGenerate` | スキーマドキュメントの生成 |

### タスクオプション

**migrapheUp**:
- `--target=<nodeId>` — 特定のノードまでマイグレーション
- `--preview` — 実行せずにプレビュー

**migrapheDown**:
- `--target=<nodeId>` — 特定のノードまでロールバック
- `--all` — 全実行済みマイグレーションのロールバック
- `--preview` — 実行せずにプレビュー

**migrapheAmend**:
- `--preview` — 何も記録せずに計画のみ表示

**migrapheGenerate**:
- `--name=<name>` — 特定のジェネレータのみ実行

プロジェクトプロパティ（`-P`）でも指定可能:

```bash
./gradlew migrapheUp -Pmigraphe.up.target=db1/create_users
./gradlew migrapheDown -Pmigraphe.down.all=true
./gradlew migrapheAmend -Pmigraphe.amend.dryRun=true
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
Failed to resolve plugin: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
```

**解決策:**
- `migraphe.yaml` の Maven 座標と `repository:` 指定が正しいか確認
- JitPack 側で `v0.6.0` のビルドが成功しているかを <https://jitpack.io/#kakusuke/migraphe> で確認
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

プラグイン JAR および Gradle プラグインは現在 JitPack 経由で `com.github.kakusuke.migraphe:<module>:v0.6.0` として配布されています。Maven Central への公開は予定中で、その際に groupId が `io.github.kakusuke.migraphe` に切り替わります。

## 次のステップ

- 設計の詳細については[アーキテクチャドキュメント](../CLAUDE.md)を参照
- 翻訳については[英語版ユーザーガイド](USER_GUIDE.md)を確認
- `examples/`ディレクトリのサンプルプロジェクトを確認（利用可能な場合）

## サポート

問題や質問については:
- GitHub Issues: https://github.com/kakusuke/migraphe/issues
- ドキュメント: https://github.com/kakusuke/migraphe/tree/main/docs
