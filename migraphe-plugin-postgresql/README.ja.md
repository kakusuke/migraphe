# migraphe-plugin-postgresql

Migraphe マイグレーションオーケストレーションツール用 PostgreSQL プラグイン。

[English version](README.md)

## 機能

- PostgreSQL データベース接続管理
- トランザクションサポート付き SQL ベースマイグレーション実行
- PostgreSQL でのマイグレーション履歴追跡
- トランザクション内で実行できない DDL 文用の Autocommit モード
- スキーマドキュメントジェネレーター（`postgresql-schema` source / `postgresql-markdown` output）。PostgreSQL 固有オブジェクト（拡張・列挙型・シーケンス・関数・トリガー・マテリアライズドビュー・パーティション・ポリシー）に対応

## インストール

### JitPack 経由（推奨）

`migraphe.yaml` にプラグインを宣言:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.3.0
    repository: jitpack
```

### plugins ディレクトリ経由

Fat JAR をビルドしてプロジェクトの `plugins/` ディレクトリに配置:

```bash
./gradlew :migraphe-plugin-postgresql:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-postgresql/build/libs/migraphe-plugin-postgresql-*-all.jar your-project/plugins/
```

## 設定

### ターゲット設定

`targets/` ディレクトリにターゲットファイルを作成:

```yaml
# targets/mydb.yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
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
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
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

**ユースケース:**
- `CREATE DATABASE` / `DROP DATABASE`
- `CREATE INDEX CONCURRENTLY`
- `VACUUM`
- `CLUSTER`

## ジェネレータータイプ

このプラグインは PostgreSQL スキーマドキュメント用の source/output ジェネレーターペアを提供します。

| 種別 | タイプ | 説明 |
|------|--------|------|
| Source | `postgresql-schema` | JDBC ベーススキーマに加え、`pg_catalog` から PostgreSQL 固有メタデータ（拡張・列挙型・シーケンス・関数・トリガー・マテリアライズドビュー・パーティション・ポリシー）を抽出 |
| Output | `postgresql-markdown` | 上記 PostgreSQL 固有オブジェクトを含む Markdown ドキュメントを生成 |

### ジェネレーター設定

`migraphe.yaml` に `generators` セクションを追加:

```yaml
generators:
  - name: pg-schema-docs
    type: postgresql-markdown
    source:
      type: postgresql-schema
      target: mydb
    output-dir: docs/postgresql
    excludes:
      - schema: "information_schema"
      - schema: "public"
        table: "tmp_.*"
```

実行:

```bash
migraphe generate --name pg-schema-docs
```

## 設定フィールド

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `type` | はい | `postgresql` である必要があります |
| `jdbc_url` | はい | JDBC 接続 URL |
| `username` | はい | データベースユーザー名 |
| `password` | はい | データベースパスワード |

## 要件

- Java 21 以降
- PostgreSQL 12 以降（推奨）

## ライセンス

Migraphe プロジェクトと同じライセンス。
