# migraphe-plugin-generator-json

Migraphe マイグレーションオーケストレーションツール用 JSON 出力ジェネレータープラグイン。

[English version](README.md)

## 機能

- ジェネレーターの **output** プラグインのみ — データベース/環境タイプは提供しません
- ジェネレーターの **source** プラグインが生成した任意のデータを、整形済み JSON として **標準出力** にレンダリング
- output プラグインは source 非依存のため、任意の source（例: `migration-tree`, `jdbc-schema`）と組み合わせ可能
- マイグレーション/スキーマのメタデータを他のツールへパイプするのに便利

## インストール

### JitPack 経由（推奨）

`migraphe.yaml` にプラグインを宣言:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.4.1
    repository: jitpack
```

### plugins ディレクトリ経由

Fat JAR をビルドしてプロジェクトの `plugins/` ディレクトリに配置:

```bash
./gradlew :migraphe-plugin-generator-json:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-generator-json/build/libs/migraphe-plugin-generator-json-*-all.jar your-project/plugins/
```

## ジェネレータータイプ

このプラグインは単一の output タイプを提供します。環境（`target`）タイプは提供**しません**。

| 種別 | タイプ | 説明 |
|------|--------|------|
| Output | `output-json` | 任意の source データを整形済み JSON として標準出力にシリアライズ |

### ジェネレーター設定

`migraphe.yaml` に `generators` セクションを追加します。`output-json` は任意の source プラグインと組み合わせられます:

```yaml
generators:
  # マイグレーションツリーを JSON で標準出力へ
  - name: tree
    type: output-json
    source:
      type: migration-tree
    output-dir: docs

  # JDBC スキーマを JSON で標準出力へ
  - name: schema-json
    type: output-json
    source:
      type: jdbc-schema
      target: mydb
    output-dir: docs
```

実行:

```bash
migraphe generate --name tree
```

シリアライズされた JSON は標準出力に書き出されるため、リダイレクトやパイプが可能です:

```bash
migraphe generate --name tree > tree.json
```

### ジェネレーターフィールド

`output-json` output タイプの場合:

| フィールド | 必須 | デフォルト | 説明 |
|-----------|------|-----------|------|
| `name` | はい | — | ジェネレーター識別子（`--name` で指定） |
| `type` | はい | — | `output-json` である必要があります |
| `source.type` | はい | — | source プラグインのタイプ。任意の source が利用可能（例: `migration-tree`, `jdbc-schema`） |
| `source.target` | source 依存 | — | ターゲット名。データベース接続が必要な source（例: `jdbc-schema`）でのみ必須。`migration-tree` では省略 |
| `output-dir` | いいえ | — | 他の output プラグインとの一貫性のため受け付けるが無視される。JSON は常に標準出力へ書き出される |

この output プラグインには**ユーザー設定可能な整形オプションはありません**。常に整形済み JSON を標準出力にレンダリングします。source 固有のフィールドは選択した source プラグインが定義します。

## 要件

- Java 21 以降

## ライセンス

Migraphe プロジェクトと同じライセンス。
