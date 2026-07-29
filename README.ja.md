# Migraphe

[![CI](https://github.com/kakusuke/migraphe/actions/workflows/ci.yml/badge.svg)](https://github.com/kakusuke/migraphe/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

有向非巡回グラフ（DAG）でタスク間の依存関係を表現し、複数環境にわたるデータベース／インフラのマイグレーションを管理するオーケストレーションツールです。

[English README](README.md)

## 機能

- **DAGベースのマイグレーション**: タスク間の依存関係を明示的に定義
- **マルチ環境**: 開発／ステージング／本番をひとつの定義で管理
- **プラガブルなデータベースサポート**: PostgreSQL、MySQL、および任意の JDBC データベース
- **方言対応の SQL 文分割**: 複数文マイグレーション、ストアドプロシージャ、PostgreSQL の `DO $$...$$`、MySQL の `DELIMITER` に対応
- **プラグイン自動解決**: `migraphe.yaml` の Maven 座標から解決（Maven Central、JitPack、任意の HTTPS Maven リポジトリ）
- **再現可能なビルド**: SHA-256 ロックファイル (`migraphe.lock.yaml`) で全プラグインと推移的依存をピン留め
- **Gradle プラグイン**: `migrapheUp`/`Down`/`Status`/`Validate`/`Generate` タスクを提供
- **スキーマドキュメント生成**: JDBC／PostgreSQL／MySQL から Markdown／JSON を出力
- **並列実行**: Virtual Threads による並列実行（オプトイン）
- **レイアウト切替**: `project.scan-root` で `tasks/`／`targets/`／`environments/`／`plugins/` の探索起点をサブディレクトリに切替（CLI／Gradle 共通）
- **型安全**: Java 21 + jspecify + NullAway で構築

## インストール

```bash
# mise（推奨）— リリース tarball は bin/ と lib/ をルート直下に同梱
mise use github:kakusuke/migraphe

# または手動インストール
mkdir -p ~/.local/migraphe
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.5.0/migraphe-0.5.0.tar.gz | tar xz -C ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"
migraphe --help
```

zip／fat JAR／ソースビルドの選択肢は [ユーザーガイド → インストール](docs/USER_GUIDE.ja.md#インストール) を参照してください。

## Hello World

「プロジェクトを作成 → PostgreSQL プラグインを宣言 → マイグレーションを 1 本書く → 実行する」までの 5 分チュートリアルはユーザーガイドにあります。

1. [Maven 座標でプラグインを導入](docs/USER_GUIDE.ja.md#プラグインのインストール)
2. [プロジェクトのセットアップ](docs/USER_GUIDE.ja.md#プロジェクトセットアップ)
3. [マイグレーションを書く](docs/USER_GUIDE.ja.md#マイグレーションの作成)
4. [マイグレーションを実行する](docs/USER_GUIDE.ja.md#マイグレーションの実行)

CLI ではなく Gradle で使う場合は [ユーザーガイド → Gradle プラグイン](docs/USER_GUIDE.ja.md#gradleプラグイン) を参照してください。

## ドキュメント

- [ユーザーガイド](docs/USER_GUIDE.ja.md) ([English](docs/USER_GUIDE.md)) — インストール、設定、実行、ロールバック、ドキュメント生成、トラブルシュート
- [プラグイン開発](docs/PLUGIN_DEVELOPMENT.ja.md) ([English](docs/PLUGIN_DEVELOPMENT.md)) — 独自プラグインの作り方
- [コントリビューション](CONTRIBUTING.md) — ソースからのビルド、コーディング規約、PR ワークフロー
- [アーキテクチャ](CLAUDE.md) — 設計判断とモジュール構成

## ライセンス

Apache License 2.0
