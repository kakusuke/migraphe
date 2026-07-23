# migraphe-plugin-mysql

Migraphe マイグレーションオーケストレーションツール用 MySQL プラグイン。

[English version](README.md)

## 機能

- MySQL データベース接続管理
- トランザクションサポート付き SQL ベースマイグレーション実行
- MySQL でのマイグレーション履歴追跡（InnoDB, `utf8mb4`）
- トランザクション内で実行できない DDL 文用の Autocommit モード
- ストアドルーチン向けの再帰的 `BEGIN ... END` ブロック処理と `DELIMITER` ディレクティブ対応
- スキーマドキュメントジェネレーター（`mysql-schema` source / `mysql-markdown` output）。MySQL 固有オブジェクト（ストレージエンジン・テーブルメタデータ・トリガー・ルーチン・イベント・パーティション）に対応

## インストール

### JitPack 経由（推奨）

`migraphe.yaml` にプラグインを宣言:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.4.3
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

ドライバークラスと DB ラベルは本プラグインで固定されており、設定できません。

#### ターゲットフィールド

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `type` | はい | — | `mysql` である必要があります |
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
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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

### 複数文 SQL・BEGIN ... END・DELIMITER

`up` / `down` には `;` で区切った複数の文を記述できます。MySQL プラグインは**再帰的な `BEGIN ... END` ブロック**を処理するため、ストアドルーチン本体内のセミコロンが文の区切りと誤認されることはありません。また、本体に `;` を含むルーチンを定義する際に文の終端記号を切り替える**`DELIMITER` ディレクティブ**にも対応しています:

```yaml
# tasks/mydb/002_add_trigger.yaml
name: Add audit trigger
target: mydb
up: |
  DELIMITER //
  CREATE TRIGGER users_audit AFTER INSERT ON users
  FOR EACH ROW
  BEGIN
    INSERT INTO audit_log (entity, entity_id) VALUES ('users', NEW.id);
  END //
  DELIMITER ;
down: |
  DROP TRIGGER users_audit;
```

PostgreSQL（ドル引用符を使用）と異なり、MySQL はルーチン本体の区切りに `BEGIN ... END` ブロックと `DELIMITER` ディレクティブを用います。

### Autocommit モード

トランザクション内で実行できない DDL 文には `autocommit: true` を指定します（MySQL では多くの DDL が暗黙コミットを伴う点に注意）:

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

#### ジェネレーターフィールド

`mysql-markdown` output タイプの場合:

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `name` | はい | — | ジェネレーター識別子（`--name` で指定） |
| `type` | はい | — | `mysql-markdown` である必要があります |
| `source.type` | はい | — | source プラグインのタイプ。MySQL 全機能には `mysql-schema` |
| `source.target` | はい | — | source がスキーマメタデータを読み取るターゲット名 |
| `output-dir` | いいえ | `docs/schema` | 生成された Markdown ファイルの出力先ディレクトリ |
| `excludes` | いいえ | — | 抽出スキーマ/テーブルに適用する除外フィルターのリスト |
| `excludes[].schema` | いいえ | — | 除外するスキーマ（データベース）名にマッチする正規表現 |
| `excludes[].table` | いいえ | — | 除外するテーブル名にマッチする正規表現（`schema` と併用） |

`mysql-schema` source は単一の `target` フィールド（スキーマを抽出する対象ターゲット）を受け付けます。

### MySQL 固有のドキュメント

標準的な JDBC スキーマ（テーブル・ビュー・カラム・キー・インデックス）に加え、`mysql-schema` / `mysql-markdown` ペアは `information_schema` から抽出した MySQL 固有オブジェクトをドキュメント化します:

- **ストレージエンジン (Storage Engines)** — テーブルごとのエンジン（例: InnoDB）とテーブルオプション
- **テーブルメタデータ (Table Metadata)** — 照合順序、行フォーマット、AUTO_INCREMENT、コメント
- **トリガー (Triggers)** — タイミング/イベント付きのテーブルトリガー
- **ルーチン (Routines)** — ストアドプロシージャと関数（定義者の帰属を含む）
- **イベント (Events)** — スケジュールイベント
- **パーティション (Partitions)** — パーティション方式とパーティション一覧

## 設定フィールド

すべてのオプション表は上記の各セクションに記載しています:

- [ターゲットフィールド](#ターゲットフィールド)
- [タスクフィールド](#タスクフィールド)
- [ジェネレーターフィールド](#ジェネレーターフィールド)

## 要件

- Java 21 以降
- MySQL 8.0 以降（推奨）

## ライセンス

Migraphe プロジェクトと同じライセンス。
