# migraphe-plugin-jdbc

Migraphe マイグレーションオーケストレーションツール用の汎用 JDBC プラグイン。

[English version](README.md)

## 機能

- ドライバークラスを明示的に指定することで、**任意の JDBC 準拠データベース**で単体動作
- PostgreSQL / MySQL プラグインのベース実装（これらはドライバー/ラベルを固定し、DB 固有の DDL とメタデータを追加）
- トランザクションサポート付き SQL ベースマイグレーション実行
- JDBC 経由でのマイグレーション履歴追跡
- トランザクション内で実行できない DDL 文用の Autocommit モード
- `DatabaseMetaData` を利用したスキーマドキュメントジェネレーター（`jdbc-schema` source / `jdbc-markdown` output）

## インストール

### JitPack 経由（推奨）

`migraphe.yaml` にプラグインを宣言:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.4.3
    repository: jitpack
```

> 注意: このプラグインは JDBC ドライバーを同梱しません。対象データベースのドライバーをクラスパスに含めてください。

### plugins ディレクトリ経由

Fat JAR をビルドしてプロジェクトの `plugins/` ディレクトリに配置:

```bash
./gradlew :migraphe-plugin-jdbc:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-jdbc/build/libs/migraphe-plugin-jdbc-*-all.jar your-project/plugins/
```

## 設定

### ターゲット設定

`targets/` ディレクトリにターゲットファイルを作成します。汎用プラグインのため、JDBC の `driver_class` を指定する必要があります:

```yaml
# targets/mydb.yaml
type: jdbc
driver_class: org.mariadb.jdbc.Driver
db_label: MariaDB
jdbc_url: jdbc:mariadb://localhost:3306/myapp
username: myuser
password: mypassword
```

#### ターゲットフィールド

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `type` | はい | — | `jdbc` である必要があります |
| `jdbc_url` | はい | — | JDBC 接続 URL |
| `username` | はい | — | データベースユーザー名 |
| `password` | いいえ | — | データベースパスワード |
| `driver_class` | はい | — | JDBC ドライバークラスの完全修飾名（例: `org.mariadb.jdbc.Driver`） |
| `db_label` | いいえ | — | 出力/ログで使用される人間可読のデータベースラベル |

### タスク設定

`tasks/` ディレクトリにマイグレーションタスクを作成:

```yaml
# tasks/mydb/001_create_users.yaml
name: Create users table
target: mydb
up: |
  CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
  );
down: |
  DROP TABLE IF EXISTS users;
```

#### タスクフィールド

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `name` | はい | — | 人間可読のタスク名 |
| `description` | いいえ | — | 任意の説明 |
| `target` | はい | — | このタスクを実行する対象ターゲット名（`targets/` のファイルに対応） |
| `dependencies` | いいえ | `[]` | このタスクより先に実行すべきタスク ID のリスト |
| `up` | はい | — | マイグレーション（up）時に実行する SQL |
| `down` | いいえ | — | ロールバック（down）時に実行する SQL。不可逆なマイグレーションでは省略 |
| `autocommit` | いいえ | `false` | トランザクション外で実行する（[Autocommit モード](#autocommit-モード)参照） |

### 複数文 SQL

`up` / `down` には複数の文を記述できます。汎用 JDBC プラグインは**標準ステートメントスプリッター**を使用し、文字列リテラル（`'...'`, `"..."`）と標準 SQL コメント（`--`, `/* ... */`）を尊重しつつ `;` で分割します。PostgreSQL のドル引用符や MySQL の `BEGIN ... END` のような**方言固有のブロック解析は行いません**。`;` を含むストアドプロシージャや関数本体には、文法を上書きする PostgreSQL / MySQL プラグインを使用してください。

```yaml
# tasks/mydb/002_seed.yaml
name: Seed reference data
target: mydb
up: |
  INSERT INTO roles (name) VALUES ('admin');
  INSERT INTO roles (name) VALUES ('user');
down: |
  DELETE FROM roles WHERE name IN ('admin', 'user');
```

### Autocommit モード

トランザクション内で実行できない DDL 文には `autocommit: true` を指定します。各文は単一トランザクションでまとめられず、即座にコミットされます:

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

どの文が autocommit を必要とするかは対象データベースに依存します。`CREATE DATABASE` / `DROP DATABASE` などは通常 autocommit が必要です。方言固有のケースは PostgreSQL / MySQL プラグインの README を参照してください。

## ジェネレータータイプ

このプラグインは汎用 JDBC スキーマドキュメント用の source/output ジェネレーターペアを提供します。

| 種別 | タイプ | 説明 |
|------|--------|------|
| Source | `jdbc-schema` | JDBC `DatabaseMetaData` 経由でデータベーススキーマメタデータ（テーブル・ビュー・カラム・キー・インデックス）を抽出 |
| Output | `jdbc-markdown` | 抽出したスキーマから、ディレクトリ構造と相互参照を備えた Markdown ドキュメントを生成 |

### ジェネレーター設定

`migraphe.yaml` に `generators` セクションを追加:

```yaml
generators:
  - name: schema-docs
    type: jdbc-markdown
    source:
      type: jdbc-schema
      target: mydb
    output-dir: docs/schema
    excludes:
      - schema: "information_schema"
```

実行:

```bash
migraphe generate --name schema-docs
```

#### ジェネレーターフィールド

`jdbc-markdown` output タイプの場合:

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `name` | はい | — | ジェネレーター識別子（`--name` で指定） |
| `type` | はい | — | `jdbc-markdown` である必要があります |
| `source.type` | はい | — | source プラグインのタイプ。本プラグインでは `jdbc-schema` |
| `source.target` | はい | — | source がスキーマメタデータを読み取るターゲット名 |
| `output-dir` | いいえ | `docs/schema` | 生成された Markdown ファイルの出力先ディレクトリ |
| `excludes` | いいえ | — | 抽出スキーマ/テーブルに適用する除外フィルターのリスト |
| `excludes[].schema` | いいえ | — | 除外するスキーマ名にマッチする正規表現 |
| `excludes[].table` | いいえ | — | 除外するテーブル名にマッチする正規表現（`schema` と併用） |

`jdbc-schema` source は単一の `target` フィールド（スキーマを抽出する対象ターゲット）を受け付けます。

### 出力構造

`jdbc-markdown` ジェネレーターは以下を生成します:

```
docs/schema/
└── mydb/
    └── public/
        ├── index.md              # スキーマ概要（テーブル/ビュー一覧）
        ├── tables/
        │   ├── users.md          # テーブル詳細（カラム・キー・インデックス）
        │   └── posts.md
        └── views/
            └── recent_posts.md   # ビュー詳細
```

各テーブルページには、カラム定義（名前・型・NULL 可否・デフォルト）、主キー/一意キー、相互リンク付き外部キー（imported key による **Foreign Keys** と exported key による **Referenced By** の両方）、インデックスが含まれます。

## 設定フィールド

すべてのオプション表は上記の各セクションに記載しています:

- [ターゲットフィールド](#ターゲットフィールド)
- [タスクフィールド](#タスクフィールド)
- [ジェネレーターフィールド](#ジェネレーターフィールド)

## 要件

- Java 21 以降
- 対象データベースの JDBC ドライバーがクラスパス上にあること

## ライセンス

Migraphe プロジェクトと同じライセンス。
