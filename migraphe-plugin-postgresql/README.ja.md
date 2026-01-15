# migraphe-plugin-postgresql

Migraphe マイグレーションオーケストレーションツール用 PostgreSQL プラグイン。

[English version](README.md)

## 機能

- PostgreSQL データベース接続管理
- トランザクションサポート付き SQL ベースマイグレーション実行
- PostgreSQL でのマイグレーション履歴追跡
- トランザクション内で実行できない DDL 文用の Autocommit モード

## インストール

### Fat JAR のビルド

```bash
./gradlew :migraphe-plugin-postgresql:fatJar
```

### plugins ディレクトリに配置

```bash
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
