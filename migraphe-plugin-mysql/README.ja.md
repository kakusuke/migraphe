# migraphe-plugin-mysql

Migraphe マイグレーションオーケストレーションツール用 MySQL プラグイン。

[English version](README.md)

## 機能

- MySQL データベース接続管理
- トランザクションサポート付き SQL ベースマイグレーション実行
- MySQL でのマイグレーション履歴追跡（InnoDB, `utf8mb4`）
- トランザクション内で実行できない DDL 文用の Autocommit モード
- スキーマドキュメントジェネレーター（`mysql-schema` source / `mysql-markdown` output）。MySQL 固有オブジェクト（ストレージエンジン・テーブルメタデータ・トリガー・ルーチン・イベント・パーティション）に対応

## インストール

### JitPack 経由（推奨）

`migraphe.yaml` にプラグインを宣言:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.4.1
    repository: jitpack
```

### plugins ディレクトリ経由

Fat JAR をビルドしてプロジェクトの `plugins/` ディレクトリに配置:

```bash
./gradlew :migraphe-plugin-mysql:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-mysql/build/libs/migraphe-plugin-mysql-*-all.jar your-project/plugins/
```

## 設定

### ターゲット設定

`targets/` ディレクトリにターゲットファイルを作成:

```yaml
# targets/mydb.yaml
type: mysql
jdbc_url: jdbc:mysql://localhost:3306/mydb
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
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
down: |
  DROP TABLE IF EXISTS users;
```

### Autocommit モード

トランザクション内で実行できない DDL 文の場合（MySQL では多くの DDL が暗黙コミットを伴う点に注意）:

```yaml
# tasks/admin/001_create_database.yaml
name: Create application database
target: admin
autocommit: true
up: |
  CREATE DATABASE myapp CHARACTER SET utf8mb4;
down: |
  DROP DATABASE myapp;
```

**ユースケース:**
- `CREATE DATABASE` / `DROP DATABASE`
- アプリケーショントランザクション外でサーバーに対して実行する文

## ジェネレータータイプ

このプラグインは MySQL スキーマドキュメント用の source/output ジェネレーターペアを提供します。

| 種別 | タイプ | 説明 |
|------|--------|------|
| Source | `mysql-schema` | JDBC ベーススキーマに加え、`information_schema` から MySQL 固有メタデータ（ストレージエンジン・テーブルメタデータ・トリガー・ルーチン・イベント・パーティション）を抽出。MySQL はデータベースを JDBC カタログとして公開するためカタログベースの探索を使用 |
| Output | `mysql-markdown` | 上記 MySQL 固有オブジェクトを含む Markdown ドキュメントを生成 |

### ジェネレーター設定

`migraphe.yaml` に `generators` セクションを追加:

```yaml
generators:
  - name: mysql-schema-docs
    type: mysql-markdown
    source:
      type: mysql-schema
      target: mydb
    output-dir: docs/mysql
    excludes:
      - schema: "information_schema"
      - schema: "mydb"
        table: "tmp_.*"
```

実行:

```bash
migraphe generate --name mysql-schema-docs
```

## 設定フィールド

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `type` | はい | `mysql` である必要があります |
| `jdbc_url` | はい | JDBC 接続 URL |
| `username` | はい | データベースユーザー名 |
| `password` | はい | データベースパスワード |

## 要件

- Java 21 以降
- MySQL 8.0 以降（推奨）

## ライセンス

Migraphe プロジェクトと同じライセンス。
