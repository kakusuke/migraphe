# migraphe-plugin-postgresql

Migraphe マイグレーションオーケストレーションツール用 PostgreSQL プラグイン。

[English version](README.md)

## 機能

- PostgreSQL データベース接続管理
- トランザクションサポート付き SQL ベースマイグレーション実行
- PostgreSQL でのマイグレーション履歴追跡
- トランザクション内で実行できない DDL 文用の Autocommit モード
- 関数/プロシージャ本体向けのドル引用符ステートメント分割（`$$ ... $tag$`）
- スキーマドキュメントジェネレーター（`postgresql-schema` source / `postgresql-markdown` output）。PostgreSQL 固有オブジェクト（拡張・列挙型・シーケンス・関数・トリガー・マテリアライズドビュー・パーティション・ポリシー）に対応

## インストール

### JitPack 経由（推奨）

`migraphe.yaml` にプラグインを宣言:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.4.1
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

ドライバークラスと DB ラベルは本プラグインで固定されており、設定できません。

#### ターゲットフィールド

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `type` | はい | — | `postgresql` である必要があります |
| `jdbc_url` | はい | — | JDBC 接続 URL |
| `username` | はい | — | データベースユーザー名 |
| `password` | いいえ | — | データベースパスワード（パスワード不要/外部認証の接続では省略可） |

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

### 複数文 SQL とドル引用符

`up` / `down` には `;` で区切った複数の文を記述できます。PostgreSQL プラグインは**ドル引用符文字列**（`$$ ... $$` およびタグ付き `$tag$ ... $tag$`）を解釈するため、関数やプロシージャ本体内のセミコロンは文の区切りとして扱われません:

```yaml
# tasks/mydb/002_add_function.yaml
name: Add trigger function
target: mydb
up: |
  CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
  BEGIN
    NEW.updated_at = now();
    RETURN NEW;
  END;
  $$ LANGUAGE plpgsql;

  CREATE TRIGGER users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
down: |
  DROP TRIGGER users_updated_at ON users;
  DROP FUNCTION set_updated_at();
```

MySQL と異なり、PostgreSQL は本体の区切りに（`BEGIN ... END` キーワードブロックや `DELIMITER` ディレクティブではなく）ドル引用符を使うため、追加のディレクティブは不要です。

### Autocommit モード

トランザクション内で実行できない DDL 文には `autocommit: true` を指定します:

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

#### ジェネレーターフィールド

`postgresql-markdown` output タイプの場合:

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `name` | はい | — | ジェネレーター識別子（`--name` で指定） |
| `type` | はい | — | `postgresql-markdown` である必要があります |
| `source.type` | はい | — | source プラグインのタイプ。PostgreSQL 全機能には `postgresql-schema` |
| `source.target` | はい | — | source がスキーマメタデータを読み取るターゲット名 |
| `output-dir` | いいえ | `docs/schema` | 生成された Markdown ファイルの出力先ディレクトリ |
| `excludes` | いいえ | — | 抽出スキーマ/テーブルに適用する除外フィルターのリスト |
| `excludes[].schema` | いいえ | — | 除外するスキーマ名にマッチする正規表現 |
| `excludes[].table` | いいえ | — | 除外するテーブル名にマッチする正規表現（`schema` と併用） |

`postgresql-schema` source は単一の `target` フィールド（スキーマを抽出する対象ターゲット）を受け付けます。

### PostgreSQL 固有のドキュメント

標準的な JDBC スキーマ（テーブル・ビュー・カラム・キー・インデックス）に加え、`postgresql-schema` / `postgresql-markdown` ペアは `pg_catalog` から抽出した PostgreSQL 固有オブジェクトをドキュメント化します:

- **拡張 (Extensions)** — インストール済み拡張とそのバージョン
- **列挙型 (Enums)** — ユーザー定義の enum 型とそのラベル
- **シーケンス (Sequences)** — 独立およびテーブル所有のシーケンス
- **関数/プロシージャ (Functions / Procedures)** — 言語と定義者/所有者の帰属を含む
- **トリガー (Triggers)** — テーブルトリガーとそのタイミング/イベント
- **マテリアライズドビュー (Materialized Views)** — 通常のビューに加えて定義を記載
- **パーティション (Partitions)** — パーティションテーブルとそのパーティション階層
- **ポリシー (Policies)** — 行レベルセキュリティ（RLS）ポリシー

## 設定フィールド

すべてのオプション表は上記の各セクションに記載しています:

- [ターゲットフィールド](#ターゲットフィールド)
- [タスクフィールド](#タスクフィールド)
- [ジェネレーターフィールド](#ジェネレーターフィールド)

## 要件

- Java 21 以降
- PostgreSQL 12 以降（推奨）

## ライセンス

Migraphe プロジェクトと同じライセンス。
