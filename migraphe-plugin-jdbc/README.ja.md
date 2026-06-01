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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.4.1
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

### Autocommit モード

トランザクション内で実行できない DDL 文の場合:

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

## 設定フィールド

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `type` | はい | `jdbc` である必要があります |
| `jdbc_url` | はい | JDBC 接続 URL |
| `username` | はい | データベースユーザー名 |
| `password` | いいえ | データベースパスワード |
| `driver_class` | はい | JDBC ドライバークラスの完全修飾名（例: `org.mariadb.jdbc.Driver`） |
| `db_label` | いいえ | 出力/ログで使用される人間可読のデータベースラベル |

## 要件

- Java 21 以降
- 対象データベースの JDBC ドライバーがクラスパス上にあること

## ライセンス

Migraphe プロジェクトと同じライセンス。
