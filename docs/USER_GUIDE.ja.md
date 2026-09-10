# Migraphe ユーザーガイド

[English version](USER_GUIDE.md)

## 目次

0. [0.7.0 へのアップグレード](#070-へのアップグレード)
1. [はじめに](#はじめに)
2. [インストール](#インストール)
3. [プロジェクトのセットアップ](#プロジェクトのセットアップ)
4. [設定](#設定)
5. [マイグレーションの作成](#マイグレーションの作成)
6. [マイグレーションの実行](#マイグレーションの実行)
7. [ロールバック（down）](#ロールバックdown)
8. [定義を適用済みとして記録（amend）](#定義を適用済みとして記録amend)
9. [履歴の作成（init）](#履歴の作成init)
10. [履歴のアップグレード（upgrade-history）](#履歴のアップグレードupgrade-history)
11. [ドリフトした状態の作り直し（rebuild）](#ドリフトした状態の作り直しrebuild)
12. [設定の検証（validate）](#設定の検証validate)
13. [スキーマドキュメント生成（generate）](#スキーマドキュメント生成generate)
14. [環境管理](#環境管理)
15. [高度な機能](#高度な機能)
16. [Gradleプラグイン](#gradleプラグイン)
17. [トラブルシューティング](#トラブルシューティング)

## 0.7.0 へのアップグレード

このリリースを走らせる前に、この順で 4 つ必要です。

**プラグインを再ビルドしてください——再解決では足りません。** プラグイン API はこのリリースで意図的に
一度だけ壊れます（移行を二度させないためです）: `Environment` / `EnvironmentId` は `Target` /
`TargetId` になり（定義とプロバイダも同様）、履歴の読み取りメソッドは target id を取らなくなり、
`ExecutionRecord` と `TaskResult` は持つものが増え、`MigrationNode.fingerprint` と `Task.signature`
は継承できるデフォルトの無い必須メソッドになりました。**ソース**を移行していないプラグインはコンパイル
できません（それが狙いです）。0.6.0 向けにビルドされた **jar** は読み込まれたうえで最初の呼び出しで
`AbstractMethodError` になるので、古い成果物を指したままにせず、再ビルドしたものへ coordinate を
更新してください。

**ドリフトが 1 つでもあると `up` は拒否します。** 適用後に編集されたマイグレーションがあるということは、
データベースがもうタスクファイルと一致していないということで、その上に積むのは「ファイルがもう記述していない
土台」の上に建てることです。`status` はそれを `[!]` と表示します。1 件なら `migraphe down <id>` で取り出せば、次の `up` が
現在のファイルの内容で入れ直します。すでに適用されているもので意図どおりなら `migraphe amend <id>`。
`migraphe rebuild` はそれを差分すべてに対して一度に行いますが、タスクファイルがもう宣言していない
マイグレーションも恒久的に取り出すぶん重い手です。`rebuild` 自身はドリフトで止まりません——それを
取り除くのが仕事だからです。

**すべてのタスクが `down:` か `no_way_back:` のどちらかを宣言するまで、`up` は一切走りません。**
ロールバックが無いことは、これまで 2 つの問いに同時に答えていました——著者が一方向だと決めたのか、
書き忘れたのか——そして両者を見分ける手段はありませんでした。いまはどちらも宣言しないことがエラーです:
`validate` が報告し、`up` は拒否します。拒否は名指しの部分グラフではなく実行全体に及びます。問題の
タスクを避けて `up` しても、`rebuild` が働けない状態がプロジェクトに残るからです。既存プロジェクトでは
実作業になりますが、それがこの区別の対価です。

**まず `migraphe upgrade-history` を実行してください——それまで他のすべてのコマンドが拒否します。**
このリリースの履歴テーブルには、前のリリースに無かった列があります。列が揃うまでは、それを選択する
コマンドが失敗します。だからスキーマ変更は運用者がスケジュールする手順であって、たまたま最初に
`status` を叩いた人に降りかかるものではありません。別のデプロイがまだ旧バージョンで動いていて、
この改名が触る列をまだ読んでいる場合、これが効いてきます。

```bash
migraphe upgrade-history
```

列を追加し、同じ実行の中で、タスクファイルがいまも供給できるもの——fingerprint、記録された依存、
一方向である宣言済みの理由——を、旧リリースがそれらを持たせずに書いた行へ埋めます。触るのは
**何も入っていない列だけ**です。既に記録されている値は、定義と一致していようといまいと、そのまま残します。

**埋めることが何を主張するか。** fingerprint 列より前に書かれた行は「このマイグレーションが適用された」
と述べるだけで、内容については何も述べていません。そこに今日のトークンを書き込むことは「データベースが
今日の定義と一致している」という主張です。migraphe はそれを検証できません——スキーマを読まないからです。
適用後にマイグレーションを編集していた場合、それが見逃されるのはこの 1 回だけで、次回以降は通常どおり
`[!]` として現れます。適用後に編集したと分かっているマイグレーションがあるなら、アップグレードに頼らず
ロールバックして適用し直してください。

**タスクファイルが宣言しなくなった行は、`upgrade` が届かない側です。** 埋める元になる定義が無いので
`status` に残り続けます。id を名指すと撤回されます:

```bash
migraphe amend <id>              # タスクファイルが宣言しなくなった行 — 撤回する
```

オブジェクトはデータベースに残るので、これが正しいのは別の手段で既に削除済みの場合だけです。削除する
経路は `migraphe down <id>` で、アップグレードが済んでいれば使えます。

`migraphe upgrade-history` は、このリリースを入れた後に履歴ごとに 1 度実行します。もう一度走らせても何もせず、
その旨を表示します。

**1 つの履歴データベースを 2 つのバージョンから指さないでください。** このリリースは履歴の
`environment_id` 列を `target_id` に改名し、時系列順のレコード id を発行します。移行済みの履歴を
0.6.0 のバイナリが読む場合も、その逆も、期待どおりには見えません。比較が必要ならコピーを移行して
ください。

## はじめに

Migrapheは、複数の環境にわたる複雑なデータベースマイグレーションを管理するために設計されたマイグレーションオーケストレーションツールです。マイグレーションタスク間の依存関係を表現するために有向非巡回グラフ（DAG）を使用し、正しい順序で実行されることを保証します。

### 主要な概念

- **マイグレーションタスク**: 単一のマイグレーション作業単位（例: テーブルの作成）
- **ターゲット**: データベース接続設定
- **環境**: 実行コンテキスト（開発、ステージング、本番）
- **タスクID**: ファイルパスから自動生成（例: `tasks/db1/001_create_users.yaml` → `db1/001_create_users`）
- **依存関係**: 実行順序を決定するタスク間の関係
- **履歴**: データベースに保存された実行済みマイグレーションの記録

### どのコマンドを使うか

migraphe が読むのは 2 つ、書くのも 2 つです。読むのは**タスクファイル**と**履歴テーブル**、書くのは
**データベース**（`up`・`down`・`rebuild`）と**履歴**（`amend`）。判断のためにデータベース自体を覗くことは
ありません——だから両者が食い違ったとき、どちらが正しいかを決めるのはあなたであり、ツールは修復方法を
選ばずに食い違いを報告します。

| 起きたこと | 実行するもの |
|---|---|
| 開発中にブランチを切り替えて、データベースがタスクファイルとずれた | `migraphe rebuild` — 違っているものをすべて直す |
| マイグレーションの SQL を書き直して、もう一度適用したい | `migraphe down <id>` のあと `migraphe up` |
| すでにスキーマがあるデータベースに migraphe を導入する | 現状を表す bootstrap タスクを 1 つ書き、`migraphe amend <id>`。**1 ノードで全部です**——一括で「適用済みにする」操作はありません |
| CI でデータベースとマイグレーションの一致を確認したい | `migraphe status --check` — すべて一致していなければ非ゼロで終了 |

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

#### Maven 座標

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
- `project.scan-root`（任意）: `tasks/`、`targets/`、`environments/` を探索する起点ディレクトリ。`migraphe.yaml` の親ディレクトリ起点の相対パス、または絶対パスを指定できます。未指定の場合は `migraphe.yaml` の親ディレクトリと同じ（既定値）。CLI と Gradle プラグインのどちらでも同じフィールドを参照するため挙動が一致します。
- `history.target`（必須）: マイグレーション履歴を保存するターゲット名

**例: `scan-root` でマイグレーション資材をサブディレクトリにまとめる**

```yaml
project:
  name: my-app
  scan-root: config
history:
  target: main
```

この設定では、Migraphe は `migraphe.yaml` の親ディレクトリを基準として `config/tasks/` からタスクを、`config/targets/` からターゲットを、`config/environments/` から環境設定を読み込みます。

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
- `autocommit`（オプション）: トランザクションなしで実行。素の boolean は両方向に効き、`{up, down}` で方向ごとに指定できる（[Autocommitモード](#autocommitモード)を参照）

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

### ロールバックを宣言するか、無い理由を宣言するか

**すべてのタスクはどちらか一方を必ず宣言し、どちらも宣言しないのはエラーです**——`validate` が報告し、
`up` は該当タスクだけでなく**実行全体**を拒否します。

`down:` が無いことは、これまで 2 つの異なる問いに同時に答えていました：著者が「これは一方向だ」と決めたのか、
書き忘れたのか。両者は正反対の対応を要求するのに見分ける手段が無く、書き忘れたロールバックが、その上に
建っているものすべてを黙って凍結していました。

```yaml
name: 旧監査テーブルを削除
target: db1
up: |
  DROP TABLE legacy_audit;
no_way_back: 行を復元する手段が無いため
```

`no_way_back:` はフラグではなく**理由**を持ちます。後からそこに行き当たった人に、そのまま引用されます：

```
Error: db1/003_drop_audit cannot be rolled back — no way back: 行を復元する手段が無いため
```

こう宣言されたマイグレーションは**凍結**されます。それ自体が落とせないので、その上に建っているものも
落とせず、`rebuild` は「戻せないものを壊す」のではなくそこで止まります。実際の `down:` が書けるなら常に
そちらを選び、`no_way_back:` はロールバックが嘘になる場合に使ってください。

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

#### 方向ごとに指定する

適用とその巻き戻しで必要なモードが同じとは限りません。`CREATE INDEX CONCURRENTLY` は autocommit を
要求しますが、それを取り消す `DROP INDEX` は要求しないことがあります。その場合は別々に書きます:

```yaml
name: Add index concurrently
target: db1
autocommit:
  up: true
  down: false
up: |
  CREATE INDEX CONCURRENTLY idx_users_name ON users(name);
down: |
  DROP INDEX idx_users_name;
```

どちらか片方だけ書いても構いません。素の `autocommit: true` は従来どおり両方に効き、両方の書き方が
同時にある場合は**方向指定が勝ちます**:

| 書き方 | UP | DOWN |
|---|---|---|
| `autocommit: true` | autocommit | autocommit |
| `autocommit: {up: true, down: false}` | autocommit | トランザクション |
| `autocommit: {down: true}` | トランザクション | autocommit |
| 記述なし | トランザクション | トランザクション |

適用済みのマイグレーションでどちらかのフラグを変えると fingerprint が動くので、`status` は `[!]` と
報告します。DB のオブジェクトは1つも変わっていないので、[`migraphe amend`](#定義を適用済みとして記録amend) だけで完結します。

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
| `[✓]` | 適用済みで、定義に変化が検出されていない |
| `[!]` | 適用済みだが、その後に定義が変わった — `up:` の SQL、`down:` の SQL、`autocommit` の設定、依存先のいずれか。DB と履歴のどちらを動かすべきかは、そのうち何を変えたかで決まるため、migraphe は判断せず報告だけする。[`migraphe amend`](#定義を適用済みとして記録amend) を参照 |
| `[?]` | プラグインは fingerprint を提供するが、適用時の行に記録が無いため変化を判定できない。0.7.0 より前に書かれた行がこれになる。**この行がある間は `status` 以外のすべてのコマンドが拒否する。** 解消できるのは [`migraphe amend`](#定義を適用済みとして記録amend) だけ |
| `[E]` | プラグインがこのマイグレーションの内容を報告できなかった（例外を投げたか、何も返さなかったか）。プラグイン側の不具合であり、`amend` では解消しない |

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

`down` コマンドは、指定したマイグレーションと、その上に建っているものをロールバックします。

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

**落とせないものが 1 つでもあると `--all` は実行前に拒否します。** 「all」はデータベース全体を意味し、その
一部は全体ではありません——他をロールバックして誰も頼んでいない形を残すのではなく、止めた原因を名指しして
何も触りません：

```
Error: --all means all, and 1 applied migration(s) cannot be rolled back. Nothing was rolled back:
  db1/003_drop_audit — no way back: 行を復元する手段が無いため
```

*名指し*のロールバックは事情が違います。その要求は満たせるか満たせないかのどちらかなので、凍結された
マイグレーションは単に拒否され、凍結されたものの下にあるマイグレーションは「それに押さえられている」と
報告されます。

**ロールバックが従うのはタスクファイルではなく履歴です。** どのマイグレーションが、どの順で落ちるか、どの
SQL が走るか、どのデータベースに繋ぐか——すべて適用を記録した行から来ます。実在するオブジェクトに対応して
いるのがそちらだからです。適用後に `target:` や `dependencies:` を編集しても、そのロールバックは動きません。

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

`status` が `[?]` または `[!]` を表示している場合に使います。

- `[?]` — 0.7.0 より前のバージョンで適用され、fingerprint が記録されていない。**これは任意の後始末ではありません。** 何も読み取れない行があると `status` 以外のすべてのコマンドが止まるので、`up`・`down`・`rebuild` はいずれも拒否します。解消できるのは `amend` だけです（行は定義から組み立て直して埋めるものであり、`up` はそこまで到達しません）。
- `[!]` — 適用後に定義が変わったが、その変更にロールバックは不要。解消するかどうかは判断です: 「DB の現在の状態は正しい」と宣言することになります。

ロールバックが必要になりうるのは `up:` の編集だけです。`down:` や `autocommit:` の編集、依存先の変更は、DB のオブジェクトを1つも変えずに fingerprint だけを動かします。しかも再適用で走るロールバックはどのみち編集後のものなので、`amend` だけで完結します。考える必要があるのは `up:` の編集で、コメントやフォーマッタの実行なら同じくロールバックは不要ですが、文そのものを変えたなら必要です。

逆に、間違っているのが *DB* 側であれば、`amend` ではなくロールバックして再適用してください（`migraphe down <id>` のあと `migraphe up`）。`amend` はその処理はしません。

### 基本的な使い方

`amend` には2つの形があり、どちらを使うかは `status` の表示から決まります。

**どちらの形も、現在の定義が言うとおりの行を追記します。** `amend` の意味は1つ — 以後そのマイグレーションについて履歴が報告する内容を、定義が言うとおりにする — で、書かれる行は定義が決める属性を選ばずすべて持ちます。編集も削除もしません: 既にある行はそのまま読めるので、**実際にいつ適用されたか**が、それを置き換えた主張の隣に残ります。両方の形が書き込み前にそう告げます:

```
For each migration listed, a row is appended saying what the definition says
now, so what the history reports about it becomes the current definition. The
rows already there are neither changed nor removed.
```

**マイグレーションを1つ指定する:**

```bash
$ migraphe amend db1/002_create_posts

Amend plan (history only — no database changes):

  [!] → [✓]  db1/002_create_posts - Create posts table

For each migration listed, a row is appended saying what the definition says
now, so what the history reports about it becomes the current definition. The
rows already there are neither changed nor removed.

1 fingerprint will be recorded.

Record 1 fingerprint? [y/N]: y

Recorded 1 fingerprint.
```

> **`amend` は「空欄を埋める」操作ではありません。** 名指したマイグレーションについて、定義が決める属性を
> **すべて**書きます。欠けていた部分だけではありません。アップグレードがまさにその境目です — もしその
> マイグレーションの `down:` ブロックをすでに削除していると、amend は記録済みのロールバックを空で
> 上書きします。そして `down` が実行するのは記録済みのロールバックです。名指す前にタスクファイルの
> `git diff` を確認してください。旧リリースが fingerprint 無しで書いた行を埋めるのは**このコマンドでは
> ありません** — それは `migraphe upgrade-history` で、あちらは何も入っていない列だけを触ります。

まずプレビュー（確認プロンプトも書き込みも無し）:

```bash
migraphe amend --preview db1/002_create_posts
```

マイグレーションを名指さない `migraphe amend` はエラーです。これは 1 件ずつ意図して行う主張だからです。
名指したマイグレーションに対象が無い場合は `Nothing to amend.` を表示して終了コード 0 で終わります。

### コマンドオプション

| オプション | 説明 |
|-----------|------|
| `<migration>` | 指定したマイグレーション1件の現在の定義を記録する。必須 |
| `--preview` | 何も記録せずに計画のみ表示（旧名 `--dry-run` も受け付けます） |
| `-y` | 確認プロンプトをスキップ |
| `--env <name>` | `environments/<name>.yaml` のオーバーレイを適用 |

範囲は 1 つで、それは意図的です。amend は履歴が報告していた内容をマイグレーション全体について
置き換えるので、名指して行います。以前は fingerprint を持たない行に対する一括形がありましたが、それは
旧リリースが書いた履歴の姿であり、それを補完することはマイグレーションについて何かを判断することでは
ないので、いまは `migraphe upgrade-history` に属します。

### 重要な注意点

1. **何も上書きされませんが、履歴が*報告する*内容は変わります**: `amend` は現在の定義を述べる行を追記し、すべての読み取りは最新の適用済み行を見ます。元の行は残るので、実際に適用された時刻は読み取れますが、「定義が食い違っていた」という事実を示すものは残りません。
2. **追記される行は、定義が決めるすべてを持ちます**: fingerprint、依存、ロールバック SQL、プラグインの metadata、target、`no_way_back:` の理由。`executed_at` は `amend` を実行した時刻で、実行時間は 0 です（どちらも「起きなかった実行」についての事実なので）。したがって `status` はそれ以降 amend の時刻を表示します。
3. **`[!]` は証拠を捨てる操作です**: 実際に実行された内容の fingerprint が、いまファイルに書かれている内容の fingerprint に置き換わります。`up:` の編集を DB に反映する必要があるなら、ロールバックしてください。計画表示でこの行に警告が付くのはそのためです。
4. **履歴が先に何と言っていたかは条件になりません**: マイグレーションを名指すことは「適用済みである」と意図的に主張する操作なので、履歴が一度も記録していなくても、既に一致していても、落とされた状態でも追記されます。ロールバックの失敗や再適用の失敗に隠されることもありません——読むのは**適用した行**であって、種類を問わない最新行ではないからです。断られるのは、プラグインが fingerprint を報告しないマイグレーションだけです（その行は自分について何も言えないため）。
5. **プラグイン側がロールバックを報告できる必要があります**: `amend` は何も実行しないため、記録すべきロールバックを実行結果からではなく UP タスクに尋ねます。同梱プラグインはすべて報告できます。報告できないプラグインのマイグレーションは名指しで拒否されます — 取って代わられた行のロールバックを、「定義が言うことを述べる」と称する行に写すことはしません。履歴リポジトリ側の対応は不要です（追記は記録機能だけで足ります）。

### 終了コード

| 終了コード | 意味 |
|-----------|------|
| 0 | 計画した分すべてを記録できた（対象が無かった場合、プレビューの場合、プロンプトで `N` と答えた場合も 0） |
| 1 | 計画した行を記録できなかった（書き込み時点で行が消えていた）、またはコマンドが失敗した |

## 履歴の作成（init）

`migraphe init` は `history.target` が名指すターゲットに履歴テーブルを作成します。データベースごとに
1 度、他の何よりも先に実行してください。

```bash
$ migraphe init
Created the migration history.

$ migraphe init
The migration history already exists.
```

このコマンドがあるのは、履歴の作成を**あなたがやること**にするためです。以前はどのコマンドも作成して
いたので、CI で `migraphe status --check` を回すと「まだ何も適用されていません」と報告しながら
データベースに DDL を書いていました。報告することが仕事のコマンドが、何かを変えるべきではありません。

**`migraphe up` だけは例外で、履歴が無ければ自分で作ります。** 最初のマイグレーションを適用する瞬間こそ、
そのプロジェクトの履歴が生まれるべきときだからです。新規プロジェクトは初回の `up` の前に何の儀式も
要りません。他のコマンドはすべて拒否します:

```
Error: the migration history has not been created here.
Run 'migraphe init'.
```

この拒否文が名指すのは `init` だけで、`up` は挙げません。これは意図的です。「報告できません」に対して
「ではマイグレーションを適用してください」と答えるのは、答えになっていないからです。

`init` は旧リリースが作った履歴を変更しません。それは
[`migraphe upgrade-history`](#履歴のアップグレードupgrade-history) の役目です。そういう履歴に対して
`init` は「既に存在します」と言い、他のコマンドはアップグレードするまで拒否し続けます。

### コマンドオプション

| オプション | 説明 |
|-----------|------|
| `--env <name>` | `environments/<name>.yaml` のオーバーレイを適用 |

## 履歴のアップグレード（upgrade-history）

`migraphe upgrade-history` は、マイグレーション履歴をインストール済みのバージョンが書く形に持っていきます。
旧リリースが作ったテーブルを変更する**唯一のコマンド**です。他のコマンドはすべて、履歴が無ければ作り、
あればその形に手を付けません。

この切り分けがあるのは、履歴が共有されうるからです。別のデプロイがまだ旧バージョンで動いているなら、
アップグレードが改名する列をそちらはまだ読んでいます。だからこの変更は運用者がスケジュールするもので、
たまたま最初に `status` を叩いた人に降りかかるものではありません。

```bash
$ migraphe upgrade-history-history

Upgrading the history
=====================

  rename environment_id to target_id
  add fingerprint column
  add plugin_metadata column
  add dependencies column
  add origin column
  add no_way_back column
  fill what the definitions still declare

Applied 7 upgrades.
```

アップグレードが残っている間、他のすべてのコマンドは拒否し、このコマンドを名指します:

```
Error: the migration history was written by an older release and needs 7 upgrade(s) before this
version can read it:
  rename environment_id to target_id
  ...
Run 'migraphe upgrade-history'.
```

**列を足すだけでなく、行も埋めます。** 最後のステップが、タスクファイルがいまも供給できるもの
——fingerprint、記録された依存、一方向である宣言済みの理由——を、旧リリースがそれらを持たせずに書いた
行へ書き込みます。触るのは何も入っていない列だけなので、既に記録されている値は、定義と一致していようと
いまいと、そのまま残ります。

無い fingerprint を埋めることは「データベースが今日の定義と一致している」という主張であり、migraphe は
それを検証できません——スキーマを読まないからです。実務上の意味は
[0.7.0 へのアップグレード](#070-へのアップグレード) を参照してください。

**タスクファイルが宣言しなくなった行には届きません。** 埋める元が無いので `status` に残り続けます。
`migraphe amend <id>` がそれを撤回します。

`--preview` も確認プロンプトもありません。各ステップは自前の検出で守られているので、既に現在の形に
なっている履歴に対して走らせても何も書きません:

```bash
$ migraphe upgrade-history-history
The history is already up to date.
```

新しいバージョンを入れた後、履歴ごとに 1 度実行してください。このコマンド名はリリース間で変わりません。
そのバージョンが「自分が書いたのではない履歴」に対して何をする必要があろうと、叩くのはこれです。

### コマンドオプション

| オプション | 説明 |
|-----------|------|
| `--env <name>` | `environments/<name>.yaml` のオーバーレイを適用 |

## ドリフトした状態の作り直し（rebuild）

`rebuild` は、データベースと履歴の両方をタスクファイルに合わせます。記録された内容が定義と一致しなく
なったマイグレーション——およびその上に建っているすべて——をロールバックし、そのうえでグラフ全体を
適用し直します。

```bash
migraphe rebuild              # 先に確認を求めます
migraphe rebuild --preview    # 計画だけを表示し、何も変えません
migraphe rebuild -y           # 確認を省略します
```

**マイグレーションの指定は取りません。** 指定するというのは `migraphe down <id>` に続けて `migraphe up` を
打つことで、それは既にできます。しかも仕事を中途半端に終わらせる手段になるので、黙って無視するのではなく
エラーで拒否します。

これは**開発時のコマンド**です。実在のオブジェクトを落として作り直すので、その中のデータは残りません。

### 一部は恒久的な削除です

履歴が持っていて、タスクファイルがもう宣言していないマイグレーションは、他と同じように落ちます——しかし
戻ってきません。適用すべき定義が残っていないからです。確認プロンプトは、実行前にそれらを別枠で名指し
します：

```
Migrations to rebuild:

  [!] db1/002_add_email

Permanently removed — no task file declares these any more, so they come out and do not go back:

  [-] db1/004_experiment

Rolling back 2 migration(s) — everything standing on them comes down too — then applying the whole graph.
```

### 拒否する条件

`rebuild` は、実行を止めうるものを**破壊的な相の前に**——そして `--preview` が戻る前に——すべて調べます。
本番の実行が失敗する計画に対してプレビューが 0 で終わるなら、それはリハーサルではないからです。次の場合に
止まります：

- 適用済みの行が、このプロジェクトがもう設定していない target を名指している（そのマイグレーションを
  取り出す接続が存在しない）
- プラグインが「そのマイグレーションが何を適用したか」を報告できない——`amend` が直す状態ではなく、
  プラグイン側の不具合
- 落とさなければならないものが `no_way_back:` を宣言している——完走する筋書きが無いので、何も壊しません
- 履歴が「定義と一致しているか」を答えられない——先に `migraphe upgrade-history` を実行してください

### 終了コード

| 終了コード | 意味 |
|-----------|------|
| 0 | 作り直しが完了した／作り直す対象が無かった／プレビューだった／プロンプトで `N` と答えた |
| 1 | 何かが実行を拒否した、ロールバックが完了しなかった、または再適用が失敗した |

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
- `fingerprint`: 適用した定義のフィンガープリント。UP成功時のみ記録される。JDBC / PostgreSQL /
  MySQL プラグインは4つをまとめてハッシュする — `up:` のSQL、`down:` のSQL、`autocommit` の
  両方向のフラグ、そして直接・間接を問わずこのマイグレーションが依存する全マイグレーション。SQLは前後の空白だけを
  除き、それ以外は書かれたまま扱う。DOWNの行、0.7.0 より前に書かれた行、プラグインが提供しない場合は
  空になり、いずれも「変更なし」ではなく「不明」を意味する

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
| `migrapheInit` | 履歴テーブルの作成 |
| `migrapheValidate` | 設定ファイルの検証（オフライン、DB接続不要） |
| `migrapheStatus` | マイグレーション実行状況の表示 |
| `migrapheUp` | マイグレーション（前進）の実行 |
| `migrapheDown` | ロールバック（後退）の実行 |
| `migrapheAmend` | 名指した1件の現在の定義を適用済みとして記録（履歴のみ） |
| `migrapheRebuild` | ドリフトした分をロールバックし、全体を適用し直す |
| `migrapheUpgradeHistory` | 履歴テーブルをこのバージョンが書く形に持っていく |
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
- `--migration=<nodeId>` — 現在の定義を記録する対象のマイグレーション（必須）
- `--preview` — 何も記録せずに計画のみ表示

**migrapheInit**: 固有のオプションはありません。履歴を作成するか、既にあればその旨を表示します

**migrapheUpgradeHistory**: 固有のオプションはありません。各ステップが守られているので、既に現在の形になっている
履歴に対して走らせても何も書きません

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
- Gradle プラグインを使う場合は、代わりに `migraphePlugin` コンフィギュレーションに座標を追加
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
