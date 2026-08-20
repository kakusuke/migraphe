# Migraphe Development Changelog

Claude session records. Newest entries first. The latest session summary also lives in [CLAUDE.md](../CLAUDE.md); full history is kept here.

### 2026-08-20 (Session 68)
- **`mysql-markdown` のルーチン出力が空だった問題を修正 — 引数表と定義本体を出力し、PostgreSQL 側の関数本体も対称化**(MariaDB 利用者からの報告 #4)
  - **バグの本質**: `MySQLSchemaInfoProvider.extractRoutines()` は `information_schema.PARAMETERS` を**一度も引いておらず**、`MySQLRoutineInfo.parameterList` に**空文字をハードコード**していた(record の Javadoc にも "currently always empty" と明記されていた = 意図的な未実装)。加えて `ROUTINE_DEFINITION` を SELECT していなかったため定義本体も存在しなかった。結果、ルーチン詳細ページには `| Parameters | |` という空欄行だけが出て、本体セクションは無かった。報告者環境では DB 側に情報がある(parameters 7 件 / routine_definition 22,291 文字)。
  - **引数の取得**: `information_schema.PARAMETERS` を `WHERE SPECIFIC_SCHEMA = ? AND ORDINAL_POSITION > 0` で引き、`RoutineKey(schema, name, type)` レコードでグルーピング。
    - **`ROUTINE_TYPE` をキーに含めるのが必須**: MySQL はプロシージャと関数を別名前空間に持つため**同名の PROCEDURE と FUNCTION が共存できる**。名前だけでキーイングすると両者の引数リストがマージされる(Session 65 で直した FK 集約バグと同型)。手動ミューテーション(キーから型を落とす)で該当テストが `["f1", "p1", "p2"]` と混ざって落ちることを確認済み。
    - **`ORDINAL_POSITION > 0` フィルタ**: FUNCTION は戻り値を `ORDINAL_POSITION = 0`(`PARAMETER_MODE` / `PARAMETER_NAME` が NULL)の行として報告する。引数ではないので SQL 側で除外する。戻り型は従来どおり `ROUTINES.DTD_IDENTIFIER` 由来の `Data Type` 行で出る。
    - **`DTD_IDENTIFIER` の値はサーバ差がある**(MariaDB 10.1 は `int(11)`、MySQL 8.0 は `int`)。テストは型文字列を固定せず「非空であること」だけを検証する。
  - **定義本体**: `ROUTINES` の SELECT に `ROUTINE_DEFINITION` を追加。**権限が無いと NULL になる仕様**(MySQL 8.0.20+ は `SHOW_ROUTINE` 権限、5.x は `mysql.proc` への SELECT)なので、null / 空白ならセクションを出さない(空の見出しを出さない)。
  - **PostgreSQL の対称化**: `PostgreSQLFunctionInfo` に `@Nullable String definition` を追加し、抽出クエリに `p.prosrc` を追加。**`pg_get_functiondef()` は使わない** — CREATE 文全体を返せる代わりに集約関数・ウィンドウ関数でエラーになるため、MySQL の `ROUTINE_DEFINITION` と同じ「本体だけ」のセマンティクスになる `prosrc` を選んだ。**引数は構造化しない**(`pg_get_function_arguments()` の 1 行文字列を維持) — サーバが既定値 / VARIADIC / OUT を正しく整形した文字列を分解するのは回帰リスクが高い。MySQL は表・PG は 1 行という非対称は意図的。
  - **共通化**: コードフェンス生成は `JdbcMarkdownGenerator.appendDefinitionSection(StringBuilder, @Nullable String)` に `protected static` で置き、MySQL / PostgreSQL 両ジェネレータが共有する。**フェンスは本体中の最長バックティック連より 1 本長く**するため、本体が Markdown フェンスを含んでいてもブロックが早期終了しない。引数表のセルは `|` と改行をエスケープ(既存の `formatIndexRemarks` の慣習に倣う)。
  - **破壊的変更**: `MySQLRoutineInfo.parameterList` (String) → `parameters` (`List<MySQLParameterInfo>`)、および `definition` の追加。`PostgreSQLFunctionInfo` にも `definition` を追加。**いずれも既存の引数個数のコンビニエンスコンストラクタを維持**したため、`MySQLRoutineInfo` の 5 番目の引数を `List.of(...)` に変える以外の呼び出し側修正は不要(PG は完全に後方互換)。
  - **テスト**(11 本追加、全 1,051 テスト green):
    - `MySQLSchemaInfoProviderTest`(Testcontainers `mysql:8.0`)4 本 — IN/OUT/INOUT の位置・モード・名前、FUNCTION の戻り値行が除外されること、`ROUTINE_DEFINITION` の取得、**同名 PROCEDURE / FUNCTION の引数が混ざらないこと**。
    - `MySQLMarkdownGeneratorTest` 5 本 — `## Parameters` 表、`## Definition` のフェンス済みブロック、引数も本体も無いときにセクションを出さないこと、本体が ``` を含むときフェンスが 4 本に伸びること、`|` を含む型(`enum('x|y')`)のエスケープ。
    - `PostgreSQLSchemaInfoProviderTest` / `PostgreSQLMarkdownGeneratorTest` 各 1 本 — `prosrc` の取得と `## Definition` の出力。
  - **検証**: `./gradlew clean build --warning-mode all` で **ErrorProne/NullAway 警告ゼロ**、全モジュール green(1,051 テスト)、spotless 適用済み。
  - **⚠️ リリース時の注意点**:
    - **minor bump 相当**: 設定を変更していない既存ユーザーの生成物が変わる(ルーチン詳細ページに `## Parameters` と `## Definition` が増え、プロパティ表からは空欄だった `| Parameters | |` 行が消える。PG の関数ページには `## Definition` が増える)。
    - **公開 record のコンポーネントが変わる**ため、`MySQLRoutineInfo` をカノニカルコンストラクタで直接構築している外部コードはコンパイルエラーになる(`parameterList()` アクセサも消滅)。
    - 定義本体を出力するようになるため、**ルーチン本体に機密情報を書いているプロジェクトでは生成ドキュメントの共有範囲に注意**が必要。
    - 22,291 文字級の本体でも Markdown としては問題ないが、ページサイズは本体の長さに比例して増える(ER 図と違いレンダラー側の上限には当たらない)。

### 2026-08-20 (Session 67)
- **MySQL/MariaDB 5.5 系で履歴テーブルが作成できない問題を修正 + `executedNodes()` から window 関数を除去**(利用者報告 #1)
  - **報告内容**: MariaDB 5.5 で `migraphe_history` を作れない。`KEY (node_id, environment_id)` が utf8mb4 の `VARCHAR(255)`×2 = 2040 バイトで InnoDB の 767 バイト制限を超過する。
  - **違反は 2 箇所だった**: 報告にあった複合インデックス(2040 バイト)のほかに、**`id VARCHAR(255) PRIMARY KEY` = 1020 バイト**も超過しており、DDL 内の定義順ではこちらが先に失敗する。MySQL 5.7+ / MariaDB 10.2+ では DYNAMIC 行フォーマットが既定で上限が 3072 バイトになるため、どちらも 5.5 世代でしか露出しない。
  - **修正(案1: utf8mb4 維持)**: 識別子列の**文字セットは utf8mb4 のまま**、インデックス対象の長さだけを制限した。
    - `id VARCHAR(64)` — 値は常に `UUID.randomUUID().toString()`(36 文字固定)で、コード全体を検索しても `WHERE id = ?` は存在せず読み出し専用のため、狭めても実害がない。
    - `INDEX idx_migraphe_history_node_env (node_id(100), environment_id(60))` = 640 バイト、`INDEX idx_migraphe_history_env (environment_id(60))` = 240 バイト。`node_id` / `environment_id` の**列幅は `VARCHAR(255)` のまま据え置き**(格納する値を壊さない)。
    - プレフィックスインデックスでも等価検索は正しく動く(MySQL がプレフィックスで絞ってから行の実値を再照合する)。実測 `EXPLAIN`: `type=ref, key=idx_migraphe_history_node_env, key_len=644, rows=1`。
  - **`CHARACTER SET ascii` 案(報告者の提案)を却下した理由**: `node_id` は `TaskIdGenerator` が `tasks/` 配下のファイルパスから生成するため**非 ASCII になり得る**(`tasks/ユーザ作成.yaml` → `ユーザ作成`)。実機検証の結果、(a) 非 strict モード(5.5 系の既定に近い)では INSERT が **`Warning 1366` だけで通り値が壊れる**、(b) utf8mb4 接続のクライアントから `WHERE node_id = ?` に非 ASCII 値を渡すと **`ERROR 1267 Illegal mix of collations`** で検索自体が落ちる。510 バイトに収まる利点より静かな破損リスクが重い。
  - **移行処理は入れない**: `initialize()` は `CREATE TABLE IF NOT EXISTS` 一発なので、既存テーブルは DDL を変えても無影響(新規作成のみ修正が効く)。旧スキーマの広い列幅は害がないため、バージョン検知 + ALTER の事故リスクを避けた。
  - **汎用 JDBC DDL も併せて修正**: `migraphe-plugin-jdbc` 側の `id VARCHAR(255)` も同じ理由で 64 に狭めた(`type="jdbc"` + MySQL 5.5 では PK 単体で 767 違反になる)。PostgreSQL 側は `TEXT` なので変更不要。
  - **`executedNodes()` の window 関数を除去**: `ROW_NUMBER() OVER (PARTITION BY node_id ORDER BY executed_at DESC)` は MySQL 8.0+ / MariaDB 10.2+ 専用で、5.5 世代では `ERROR 1064`(構文エラー)になる。相関サブクエリ `h.executed_at = (SELECT MAX(h2.executed_at) ...)` + `SELECT DISTINCT` に書き換え、H2 / PostgreSQL / MySQL / MariaDB で共通に動く形にした。**タイ時の意味は意図的に変わる**: 同一 node_id で `executed_at` が最大値タイのとき、`ROW_NUMBER` 版は任意の 1 行を選んでいたが、新実装はタイ行のいずれかが成功 UP なら適用済みと判定する。`HistoryRepository.executedNodes` は core/CLI/Gradle から呼ばれておらず(`up`/`down`/`status` はすべて `wasExecuted` 経由)、影響は直接 API を叩く利用者のみ。
  - **テスト**(`MariaDBLegacyCompatibilityTest`、4 本 green): `mariadb:10.1` を使う。**`mariadb:5.5` は arm64 イメージが無い**が、10.1 は `innodb_file_format=Antelope` / `innodb_large_prefix=0` が既定で 767 制限を再現し、window 関数も未実装(10.2 で追加)で、しかもサーバ自身が `5.5.5-10.1.48-MariaDB` と申告する。つまり**1 コンテナで両方の不具合を再現できる**。`MariaDBContainer` ではなく `MySQLContainer` にイメージを差し替えて使っているのは、`jdbc:mysql://` URL を得るため(`MySQLEnvironment` はドライバが MySQL 固定)。修正前の実測失敗は `ERROR 1071 Specified key was too long` と `ERROR 1064`。サーバが実際に 767 制限を強制していることを確認する canary テスト(`SELECT @@innodb_large_prefix` = 0)も置いた。非 ASCII の `node_id` 往復テストで utf8mb4 維持の判断を固定。
  - **⚠️ 副産物として発見した別バグ(今回は未修正)**: **MariaDB では `executed_at` の秒未満が失われる**。Connector/J はサーバのバージョン文字列で小数秒送信の可否を決めるが、MariaDB は自身を `5.5.5-10.1.48-MariaDB` と申告するため、ドライバが「MySQL 5.6.4 未満 = 小数秒非対応」と判断してクライアント側で切り捨てる。列が `TIMESTAMP(6)` でも実測で 3 行すべて `micros=0`(約 20ms 間隔の INSERT)。結果として `wasExecuted()` の `ORDER BY executed_at DESC LIMIT 1` は、同一秒内に UP と DOWN が並ぶと **MariaDB では既に非決定的**。恒久対策には挿入順を表す単調増加列(スキーマ変更 + 移行)が必要なため今回のスコープ外とし、テスト側は明示的な秒単位タイムスタンプを書いて壁時計順序に依存しない形にした。
  - **リリース時の注意点**: bugfix(patch bump 相当)。既存テーブルには一切触らないため、既存環境の挙動は `executedNodes()` のタイ semantics を除いて不変。

### 2026-08-20 (Session 66)
- **MySQL 方言の文分割が `DROP TABLE IF EXISTS` を多数含むスクリプトで指数爆発する不具合を修正**(利用者からの報告 #2「down が文数の多いタスクでハングする」)
  - **症状**: MariaDB 利用者から「83 文の `DROP TABLE IF EXISTS` を持つ down タスクが『Executing rollback...』から返らない」との報告。同タスクの up(83 文の `CREATE TABLE`)は 1.1 秒、7 文の DROP は 77ms、手動実行は 0.14 秒。autocommit の有無・`--all` の有無・MariaDB 5.5/11.4 のいずれでも再現。**DB に到達する前の文分割で詰まっていた**ため、接続設定やサーバ側の要因ではない。
  - **原因**: `MySqlGrammar.block()` の `ifBlock = seq(keyword("IF"), body, keyword("END"), ws, keyword("IF"))` が `DROP TABLE **IF** EXISTS` の `IF` にマッチし、`body = many(content)` が対応する `END` を探して入力末尾まで走査してから失敗する。ところが `content` に `ref(block)` が含まれるため、**走査中に出会う後続の `IF` ごとに同じ全走査を再帰的にやり直す**。偽 `IF` が k 個あると T(k) = n + ΣT(j) となり **O(2^k)**。実測で 2 文増えるごとに約 4 倍(n=22 で 9.7 秒)、n=83 は事実上ハング。
  - **重要**: **文法の判定結果自体は正しかった**。`END IF` が無いので偽 `IF` はきちんと棄却され、`BEGIN ... DROP TABLE IF EXISTS tmp; ... END` も 1 文と認識できていた。**壊れていたのはコストのみ**。
  - **修正**: `SqlParsers.memoize(SqlParser)` を公開コンビネータとして追加し、`MySqlGrammar.block()` が組み立てたブロックパーサ(= `ref` が指す再帰参照そのもの)をラップした。パッカラート方式で位置ごとの結果を 1 度だけ計算する。**判定は定義上まったく同一**(パーサは (入力, 位置) の純関数)なので、認識されるスパンは一切変わらない。
    - **実装**: `MemoizingParser`(private static final class)が `@Nullable Memo memo` フィールドに直近入力ぶんの表を持つ。`Memo` は `final String sql` + `final int[] results`。
    - **番兵は `UNCOMPUTED = -2`**(レビュー指摘により当初のバイアス加算方式から変更)。`SqlParser.parse` の戻り値の定義域は `{-1} ∪ [0, sql.length()]` なので `-2` は決して有効な結果にならない。表を `-2` で先に埋めておけば**結果を素のまま格納**でき、読み書きごとの `+2` / `-2` も定数 2 つも不要になる。`parse` の戻り値契約を広げるとこの前提が壊れる旨を Javadoc に明記した。
    - **先埋めは `Memo` のコンストラクタ内で完了させる必要がある**(作法ではなく正しさの要件)。final フィールドの凍結(JLS 17.5)が保証するのはコンストラクタ完了時点の配列内容までなので、公開後に埋めると競合読者が既定値 `0` を観測しうる。バイアス方式では `0` は「未計算」を意味して安全だったが、`-2` 方式では **`0` は「位置 0 でマッチし何も消費しなかった」という有効な結果に化ける**。
    - **キャッシュ無効化に `String.equals` を使う理由**: 同一オブジェクトなら `String.equals` が内部で `this == other` により短絡するので実質 O(1)。かつ**内容が等しい別インスタンスでキャッシュを再利用しても健全**(パーサは内容の純関数)。`==` による参照比較は ErrorProne の `ReferenceEquality` に触れるため避けた。
    - **`ThreadLocal` を使わなかった理由**: 当初 `ThreadLocal` 案で実装したが ErrorProne の `ThreadLocalUsage`(ThreadLocal は static フィールドに置くべき)に触れた。`@SuppressWarnings` を使わず根本対応するため、**final フィールドによる安全公開に依拠した良性データ競合(racy single-check)**へ変更した。`Memo` のフィールドが final なので JLS 17.5 により競合読者が半端な表を観測することはなく、委譲先が純関数なのでキャッシュミスは「同じ値の再計算」で済む。結果的に ThreadLocal より単純・高速でメモリも少ない。
  - **効果(修正後の実測、`DROP TABLE IF EXISTS` × n)**: n=22 が 9,720ms → **2.8ms**、n=83 がハング → **6.0ms**、n=300 が 46.4ms、n=1000 が 426.7ms。
  - **残存する最悪計算量**: O(文字数 × 偽ブロック開始キーワード数)。指数は消えたが線形ではない(上表の n=1000 が該当)。真に線形化するには「ブロック開始キーワードを文頭に限定する」文法変更(B 案)が必要で、ヒューリスティックの誤判定リスク検証を伴うため**別サイクルに分離**した。
  - **テスト(計 6 本追加、1,036 テスト green)**:
    - `MySqlGrammarTest` に `@Nested NonBlockIfKeyword` を追加(3 本)。`DROP TABLE IF EXISTS t0..t82;` が 83 文に分割されること、`CREATE TABLE IF NOT EXISTS` × 83 でも同様であることを `assertTimeoutPreemptively(Duration.ofSeconds(2))` で固定。修正前は 2 本とも `execution timed out after 2000 ms` で失敗。修正後は 16ms / 12ms。
    - 3 本目は characterization テスト: `CREATE PROCEDURE p() BEGIN DROP TABLE IF EXISTS tmp; SELECT 1; END` が 1 文であること。**偽 `IF` を棄却する既存の正しい挙動**を固定し、memo 化が意味を変えていないことを担保する。
    - `SqlParsersTest` に `memoize` の契約テストを 3 本追加。委譲先の結果をマッチ/非マッチ両方でそのまま返すこと、同一位置は 1 度しか計算しないこと(呼び出し回数カウンタで検証)、**入力が変わったらキャッシュを破棄すること**(`"BEGIN"` → `"BREAK"` → `"BEGIN"`。同じ長さなので、無効化が壊れると誤って一致を報告する)。
  - **up 側も同じ地雷を踏んでいた**: `CREATE TABLE IF NOT EXISTS` を 83 文使えば up でもハングする。報告者の up が 1.1 秒で済んでいたのは、その CREATE に `IF NOT EXISTS` が無かったため。down 固有の問題ではない。
  - **PostgreSQL 方言は無影響**: `PostgreSqlGrammar` はキーワードブロックを持たない(`BEGIN;`/`COMMIT;` を独立した文として分割するための意図的な設計)ため、そもそもこの経路に入らない。`StatementSplitter` / `standardRegion()` も未変更。
  - **⚠️ リリース時の注意点**: **bugfix(patch bump 相当)**。分割結果は変わらず、これまでハングしていた入力が返るようになるだけ。公開 API に `SqlParsers.memoize(SqlParser)` が 1 つ増えた(既存シグネチャの変更はなく、`SqlParser` インターフェースも未変更)。バージョン bump は session end のスコープ外(`migraphe-version-up` skill で明示的に実施)なので本 PR には含めていない。

### 2026-07-29 (Session 65)
- **`JdbcSchemaInfoProvider.buildKeyInfo` の潜在バグを修正 — exported keys で子テーブルが静かに消える問題**(Session 64 の将来課題 2 を消化)
  - **バグの本質**: `buildKeyInfo(ResultSet, boolean imported)` は `DatabaseMetaData.getImportedKeys()` と `getExportedKeys()` の**両方向を処理する共通ヘルパー**で、複合列 FK を 1 つの `JdbcForeignKeyInfo` に集約するための builder マップを **`FK_NAME` のみ**でキーイングしていた。`getExportedKeys()` の結果セットには**複数の異なる子テーブルの行が混ざる**ため、異なる子テーブルが同名の FK 制約を持つと同じ builder にマージされ、列が二重に追加されたうえ `referencedTable` が後続行で上書きされ、**子テーブルが 1 つ静かに消えていた**。「両方向を 1 つのヘルパーで扱う」構造ゆえに、集約キーが imported 側の前提(単一テーブル)のままで exported 側だけが壊れていた形。
  - **修正**: builder マップのキーを `private record BuilderKey(String fkTableSchem, String fkTableName, String fkName)` の複合キーに変更。`FKTABLE_SCHEM` / `FKTABLE_NAME` を `imported` 分岐の**外側**で `nullToEmpty(...)` 経由で読む。`ForeignKeyBuilder` クラス自体は未変更。
  - **imported 方向は no-op である根拠**: `getImportedKeys` は単一テーブルに対する呼び出しなので、JDBC 仕様上の契約として全行で `FKTABLE_SCHEM`/`FKTABLE_NAME` が同一値になる。スキーマ未対応 DB で `FKTABLE_SCHEM` が NULL のケースでも `nullToEmpty` で全行一様に `""` へ正規化されるため、複合キーは行をまたいで一致し集約挙動は変わらない。
  - **影響範囲は `exportedKeys()` を消費する箇所のみ** = Markdown ジェネレータの `## Exported Keys` セクション。**ER 図には影響しない** — 子孫方向の走査は imported FK の逆インデックスを使っており `exportedKeys()` を読まない(Session 64 の設計判断どおり)。
  - **テスト**(`JdbcSchemaInfoProviderTest`、H2 実 DB ベース、計 8 本 green):
    1. `getSchemaInfoReturnsExportedKeysForChildTablesInDifferentSchemasWithSameConstraintName` — バグ再現テスト。**H2 は制約名をスキーマスコープでユニーク管理するため同一スキーマ内に同名 FK 制約を作れない**。よって子テーブルを別スキーマ(`s1.child_a` / `s2.child_b`)に置き、共通の親 `PUBLIC.parent` を同名 `fk_shared` で参照させることで、スキーマ横断で返る `getExportedKeys` 上に `FK_NAME` 衝突を作った。修正前の実測失敗は `Expected size: 2 but was: 1` で、残った 1 件は `columns=[ID, ID]` / `referencedColumns=[PARENT_ID, PARENT_ID]` と列が二重化し `referencedTable=CHILD_B` に上書きされていた。
    2. `getSchemaInfoAggregatesMultiColumnForeignKeyIntoSingleEntry` — characterization テスト。複合主キー `parent_composite(a, b)` を 2 列で参照する `child_composite` を作り、複合キー化後も imported / exported 両方向で 2 行が 1 エントリに集約されることを固定。H2 は複合 FK の列を KEY_SEQ(宣言順)で返すため `containsExactly` で順序も固定。
  - **tidy**: `if (fkName == null) fkName = "";` の手書き正規化を既存の `nullToEmpty(...)` ヘルパーに寄せ、effectively-final のための `String finalFkName` 中間変数を削除(`k -> new ForeignKeyBuilder(k.fkName())`)。テスト側で 3 箇所重複していた全スキーマ横断のテーブル検索を private static ヘルパー `findTable(JdbcSchemaInfo, String)` に抽出。
  - **⚠️ リリース時の注意点**: **bugfix(patch bump 相当)**。ただし**同名 FK 制約を持つ複数の子テーブルが存在するスキーマでは `## Exported Keys` セクションの行数が増える**ため、設定を変更していない既存ユーザーでも生成される Markdown が変わる(これまで欠落していた子テーブルが正しく現れる)。
  - **既知の残課題(今回のスコープ外、別軸のバグ)**: `FK_NAME` が NULL(`""` に正規化される)で、**同一子テーブルに複数の無名 FK 制約**が存在する場合、複合キー化後も依然として 1 つの builder にマージされ列が混ざる。今回修正した「子テーブル跨ぎのマージ」とは別軸の問題。H2 は無名制約に自動でユニーク名を割り当てるため H2 では再現困難。

### 2026-07-28 (Session 64)
- **Markdown ER 図に (A) レイアウトエンジン指定 `er-diagram-layout`、(B) テーブル別近傍 ER 図 `er-diagram-per-table`、(C) サイズ安全弁 `er-diagram-per-table-max-entities` を追加**
  - **(A) `er-diagram-layout`(既定 `elk`)**: `index.md` の Mermaid ER 図フェンス冒頭に YAML frontmatter(`---\nconfig:\n  layout: elk\n---`)を出力してレイアウトエンジンを指定する。**動機**: ER 図が横長になり線が交差して読み難く、Mermaid 公式ドキュメントが ELK を「大きく複雑な図に推奨」としているため。値は `VALID_LAYOUT_NAME_PATTERN`(`[A-Za-z0-9_-]+`)に完全一致するものだけを許可し、それ以外・空文字・null なら frontmatter 自体を省略する(不正な値で図を壊さない)。フィールドは `@Nullable String` + コンストラクタで空文字に正規化(既存の `nullToEmpty` 慣習を踏襲)。
  - **(B) `er-diagram-per-table`(既定 `true`)**: 各テーブルページ(`<outputDir>/<name>/<schema>/tables/<table>.md`)のヘッダ直後・`## Columns` の前に、そのテーブルの**近傍 ER 図**を出力する。
    - **近傍の定義**: `{T} ∪ 祖先*(T) ∪ 子孫*(T)`。**無向連結成分ではない** — 兄弟方向(「祖先の別の子孫」「子孫の別の祖先」)へは辿らない。
    - **新規実装**: `TableRef` record、`FkGraph` record(forward/backward の隣接マップ + 正準順テーブル列)、遅延初期化 `fkGraph()`、`buildFkGraph()`、`collectReachable()`、`neighborhoodOf()`。
    - **遅延初期化が必須の理由**: `nonExcludedTables()` が `protected boolean isTableExcluded(...)` を呼ぶため、コンストラクタから FK グラフを組むと ErrorProne の `ConstructorInvokesOverridable` 警告が出て「警告ゼロ」ゲートを破る。
    - **`collectReachable()` は呼び出しごとに独立した `visited` を持つ**(critical constraint)。`result`(和集合)を visited として共有すると、祖先側で訪問済みのノードから子孫探索が始まり方向が混ざる。
    - **`appendErDiagramSection(StringBuilder, List<SchemaTable>)` を抽出**し index.md 用と共有。**2 パス構造(全エンティティ → 全リレーション)を維持**する。1 パスに寄せると、後続テーブルのエンティティが先行テーブルの FK から参照されるケースで index.md の出力順が静かに変わる。
    - **子孫方向は `exportedKeys()` を使わず、全テーブルの `foreignKeys()`(imported)から逆インデックスを構築**する。理由: (1) 辺の描画元と真実の源を 1 つに保つ (2) 既存テストフィクスチャが exportedKeys を埋めていない (3) `JdbcSchemaInfoProvider.buildKeyInfo` が FK_NAME のみをキーに集約するため exported keys 側は制約名衝突で子が静かに消える潜在バグがある (4) `resolveReferencedSchema` は imported 方向を前提に書かれている。
    - クロススキーマ名の正規化は `resolveReferencedSchema` でグラフ構築時に一元化。出力順は BFS 発見順ではなく正準順序(`orderedTables()` のフィルタ)= index.md と同じ順序ルール。`er-diagram: false` が全 ER 図出力のマスタスイッチ。
  - **(C) `er-diagram-per-table-max-entities`(既定 `60`)**: 近傍のエンティティ数が上限を超えたページは、図の代わりに省略メッセージ + 全体 ER 図(`../../../index.md`)へのリンクを出力する。`0` 以下で無制限、ちょうど上限なら図を出す。**動機**: 近傍は推移閉包なのでハブテーブル経由で 200 エンティティ / 1 ページ 800KB に達しうるうえ、**GitHub の Mermaid は約 50,000 文字を超えると描画を拒否する**ため、既定 `true` のまま出荷すると既存ユーザーの図が無言で壊れる。走査の意味論(上限なしで全部辿る)は変更せず、**出力段でのみ働く退避措置**とした。
  - **配線**: 3 プラグイン(`jdbc-markdown` / `postgresql-markdown` / `mysql-markdown`)すべてで 3 設定が end-to-end で有効。3 つの generator を 8 引数コンストラクタ(telescoping)に統一し、**単一終端コンストラクタ**の形を維持した(`DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` を `protected static final` にして、サブクラスの 7 引数版が 8 引数版へ委譲できるようにした)。
  - **テスト**: テーブルページ ER 図・サイズ安全弁・エッジケース回帰テストを多数追加。エッジケース: 自己参照 FK(リレーションを 1 回だけ描く)/ 相互参照(循環)/ クロススキーマ FK の両方向走査 + スキーマ名の大小正規化 / 除外テーブルによる経路切断 / `products.md`(子孫のみの近傍)/ 上限の境界値 / `0`・`-1` で無制限 / 省略メッセージ内の `](../../../index.md)` リンク / `erDiagramKeysOnly` との組み合わせ。副産物として MySQL プラグインの `output()` がノーカバレッジだった穴も塞いだ。
  - **ユーザードキュメント**: `docs/USER_GUIDE.md` / `.ja.md` / `migraphe-plugin-jdbc/README.md` / `.ja.md` に 3 オプションを追記。あわせて出力ディレクトリ構成の記述誤り(per-schema `index.md` があると書かれていた)も修正。
  - **検証**: `./gradlew clean build --warning-mode all` で **ErrorProne/NullAway 警告ゼロ**、全モジュール green(1,028 テスト)、spotless 適用済み。
  - **⚠️ リリース時の注意点**:
    - **minor bump 相当**: 設定を変更していない既存ユーザーの出力が変わる(index.md に frontmatter が入り、全テーブルページに ER 図セクションが増える)。
    - frontmatter は **Mermaid 9.4+ 必須**。それ未満のレンダラーでは `---` が図の一部と解釈され構文エラーになりうる。
    - **GitHub の Mermaid は `@mermaid-js/layout-elk` を登録していないため `layout: elk` は dagre にフォールバック**する。GitHub 上ではレイアウト改善効果は得られない(図は壊れない)。
    - **既定値 60 の限界**: エンティティ数は文字数の代理指標にすぎない。1 エンティティ ≈ 45 文字 + 40 文字/カラム なので、8–10 カラムなら 60 エンティティ ≈ 22–28K 文字(安全)だが、20 カラムを超えると 50K 文字を超えうる → 値を下げるか `er-diagram-keys-only: true` を併用する。
    - **公開インターフェース `JdbcMarkdownDefinition` に抽象メソッドが 3 つ増えた**(手書き実装者はコンパイルエラー。SmallRye プロキシ利用なら影響なし)。
    - 空値/非数値の YAML は SmallRye が設定ロード時に例外を投げる(`SRCFG00040` / `SRCFG00039`)。既定値へのフォールバックはしない。**`er-diagram-layout:` を空値にするのは設定エラー**で、frontmatter を省略したい場合は許可文字集合外の文字を含む値(例 `" "`)を指定する。
  - **将来課題**:
    1. **`ErDiagramOptions` record の抽出** — コンストラクタが 8 引数に達した。公開 API の形状変更を伴うので独立したリリースサイクルで扱う。既存コンストラクタを `@Deprecated` で残し新コンストラクタへ委譲する方針案。
    2. **`JdbcSchemaInfoProvider.buildKeyInfo` の潜在バグ** — `LinkedHashMap` を FK_NAME のみでキーイングしているため、`getExportedKeys` の複数子テーブル行が制約名衝突時にマージされ `referencedTable` が上書きされる(子が静かに消える)。imported 側は 1 テーブル内で FK 名が一意なので実害なし。Exported Keys セクションの表示に影響しうる。
    3. **`JdbcMarkdownGenerator` が約 1,200 行** — 将来 `ErDiagramRenderer` の抽出を検討。
    4. サイズ上限をエンティティ数ではなく文字数ベースの判定に変える余地。

### 2026-07-23 (Session 63)
- **Markdown ER 図 / スキーマ出力のマルチスキーマ対応強化 + enum 型表示修正 + 実 PostgreSQL E2E テスト追加(コードレビュー起点)**
  - **クロススキーマ FK/exported-key リンク修正**: Foreign Keys / Exported Keys のリンクが `../tables/<t>.md`(同一スキーマ固定)で別スキーマ参照時に 404 だったのを `../../<referencedSchema>/tables/<t>.md` に修正。参照スキーマ名は `schemaInfo.schemas()` の既知名へ正規化(大小/綴り不一致対策)。
  - **ER 図エンティティ ID の単射化**: 旧 `sanitize(schema)_sanitize(table)` は連結が非単射で別スキーマ同名テーブルが衝突。`sanitize(schema)_sanitize(table)_<sha256(schema.length() + ":" + schema + table) 先頭8桁>` の純関数エンコードに変更(長さプレフィックスで単射)。表示は Mermaid エイリアス `id["table"]`(ラベル=テーブル名)。ER 図は 1 枚統合を維持し、スキーマの grouping は Mermaid `erDiagram` がサブグラフ非対応のため行わない。
  - **ER 図の細部修正**: 対象テーブル 0 件のスキーマでは空の erDiagram フェンスを出さない / リレーションラベル `fk.name()` をサニタイズし空名は `fk` にフォールバック / PK かつ FK のカラムに `PK, FK` を併記。
  - **enum/UDT 型の基底名表示**: PostgreSQL enum 列は JDBC が `"schema"."type"` + `COLUMN_SIZE=Integer.MAX_VALUE` を返すため、テーブル別 doc は `(2147483647)`、ER 図は `_schema___type_` に潰れていた。`cleanTypeName`(クォート除去 + 最後のドット以降 = 基底名)を導入し、番兵サイズ `Integer.MAX_VALUE` のときは `(...)` を付与しない。結果、実テーブル定義と同じ `user_account_status` 表示。
  - **cleanup**: `sanitizeMermaid` の正規表現を `static final Pattern` に precompile、除外判定の regex コンパイルをコンストラクタで 1 回だけにキャッシュ。
  - **実装**: すべて `migraphe-plugin-jdbc` の `JdbcMarkdownGenerator`(postgresql/mysql は継承で波及)。`/tdd-cycle` を複数サイクル(各 Red→Green→regression→tidy)。
  - **E2E**: `migraphe-plugin-postgresql` に Testcontainers PostgreSQL 16 の E2E テスト `PostgreSQLSchemaDocE2ETest` を追加。複数スキーマ + enum 列 + クロススキーマ FK + PK 兼 FK + 同名テーブルを実 DB に作り、抽出 → 生成 → 検証を通しで確認(全 Green)。
  - **検証**: clean build 成功・ErrorProne/NullAway 警告ゼロ・全モジュール green。wrs-japan で `~/.m2` の v0.4.2 jar をローカルビルドで再オーバーレイ + `migraphe pin`/`validate` OK。

### 2026-07-22 (Session 62)
- **ER 図に「PK/FK カラムのみ表示」オプションを追加 + ドキュメントの YAML キー修正 + wrs-japan での実地確認**
  - **新オプション**: `JdbcMarkdownDefinition` に `erDiagramKeysOnly`（boolean, `@WithDefault false`、YAML キー `er-diagram-keys-only`）を追加。`true` で ER 図の各エンティティを主キー・外部キーのカラムのみに絞る（リレーションは不変）。デフォルト `false` は全カラム表示。jdbc/postgresql/mysql の各 generator を 5 引数コンストラクタ化（3/4 引数は委譲オーバーロードで温存）し、各 plugin で `definition.erDiagramKeysOnly()` を配線。テスト 2 本追加（keys-only で非キー列が消える / デフォルト全カラム維持）。全モジュール green、clean build + ErrorProne/NullAway クリーン。
  - **ドキュメント修正**: USER_GUIDE(en/ja) が ER 図オプションの YAML キーを camelCase（`erDiagram`）で記載していたが、SmallRye `@ConfigMapping` は kebab-case（`output-dir` と同じ規約）のため `erDiagram: false` は認識されず無視される既存バグを修正。正しくは `er-diagram` / `er-diagram-keys-only`。
  - **wrs-japan での実地確認**: ローカルビルドの `migraphe-plugin-jdbc` / `migraphe-plugin-postgresql` jar を `~/.m2` の JitPack キャッシュ（`com.github.kakusuke.migraphe:...:v0.4.2`）に上書きし、`migraphe.lock.yaml` を `migraphe pin` で再ピン。実 DB で `migraphe generate` が ER 図付き Markdown を生成することを確認（デフォルト全カラム、スキーマ修飾型 `"app"."language_code"` のサニタイズも実証）。
  - **補足**: Session 61 の一部編集（識別子サニタイズ = Step 5、および PostgreSQL/MySQL 配線 = Step 7）がツール出力障害でディスクに未反映だったことを clean build で検出し、直接編集で再実装・grep 永続化確認・再検証した。

### 2026-07-22 (Session 61)
- **DB スキーマ出力 generator に ER 図（Mermaid）出力を追加（3方言すべて）**
  - **概要**: Markdown 系アウトプットプラグイン（`jdbc-markdown` / `postgresql-markdown` / `mysql-markdown`）が、DB 全体で 1 枚の ER 図を Mermaid 記法（```mermaid `erDiagram`）で `index.md` に埋め込むようにした。SVG ファイルは生成しない。
  - **仕様**: エンティティ = 各テーブル（カラム行に型・PK/FK 印。PK かつ FK のカラムは Mermaid の制約により PK 優先表示）。ビューは対象外。リレーション = 外部キー（imported）から `参照先 ||--o{ FK保持テーブル : "FK名"`。カーディナリティは現状一律 1 対多（`||--o{`）固定。参照先テーブルが出力対象に存在しない（exclude 等）FK はリレーションを描かない（空エンティティ防止）。テーブル名・カラム名・型名は Mermaid 安全な識別子にサニタイズ（非英数字を `_` に置換。例 `character varying(255)` → `character_varying_255_`）。単一スキーマ前提の割り切り。
  - **新オプション**: generator 定義（`JdbcMarkdownDefinition`）に `erDiagram`（boolean, `@WithDefault true`）を追加。`erDiagram: false` で ER 図セクションを抑制できる。
  - **実装（`/tdd-cycle` を 7 サイクル）**: erDiagram フラグ → エンティティ生成 → FK/リレーション → 参照先スキップ → 識別子サニタイズ → `false` 配線 → PostgreSQL/MySQL への波及。`JdbcMarkdownGenerator.appendErDiagram`（+ サニタイズ/参照先ガード）、`JdbcMarkdownDefinition.erDiagram()`、jdbc/postgresql/mysql 各 plugin で `definition.erDiagram()` を配線、各 generator に 4 引数コンストラクタを追加。
  - **検証**: 全モジュール green、spotless clean。
  - **将来課題**: カーディナリティ精緻化（NULL 許容 / 複合キー）、複数スキーマの厳密対応。

### 2026-06-26 (Session 60)
- **Maven Central 登録に向けた全モジュール Javadoc 整備（英語・javadoc 警告ゼロ化）**
  - **Motivation**: Maven Central は javadoc jar / sources jar を必須要求し、公開 API はプラグイン開発者が直接参照する。調査の結果、204 main ソースファイル中 Javadoc があるのは 72（35%）のみで、特に public API（`migraphe-api`）のコア SPI 9 インターフェースが完全に未文書化、PostgreSQL/MySQL プラグインは約 5% しか整備されていなかった。
  - **基盤整備（`build.gradle.kts`）**: `subprojects` の `java {}` に `withJavadocJar()` / `withSourcesJar()` を追加（全 8 モジュールで `*-javadoc.jar` / `*-sources.jar` を生成）。`tasks.withType<Javadoc>` に `Xdoclint:all`（`-quiet`）+ UTF-8 を設定し、Javadoc 警告をドキュメント品質ゲートとして可視化（`Werror=false` のためビルドは落とさない）。
  - **整備内容**: 全 204 ファイルの型（class/interface/record/enum）と public/protected メンバ（メソッド・コンストラクタ・record コンポーネント `@param`・enum 定数）に英語 Javadoc を付与。既存の日本語 Javadoc / インラインコメントも英語へ書き換え（`Result` の `value()`/`error()`、`ExecutionRecord` のコンポーネントコメント等を含め main java から日本語を一掃 = 残存 0）。SPI 型は ServiceLoader 発見と `META-INF/services` リソースを明記、実装者・呼び出し側双方の契約を記述。実装に合わせた正確な記述を徹底（例: `EnvironmentFactory.createEnvironment` の `@throws` を実体の `PluginNotFoundException` に修正）。
  - **オーケストレーション**: Explore でカバレッジマップを作成後、`migraphe-api` を 2 エージェントで先行整備して英語スタイル基準を確立（warning ゼロを先に達成）。共通スタイルガイドを scratchpad に書き出し、残りを「編集専任エージェント（gradle 非実行）」17 並列でパッケージ単位に分担 → ルートビルドロック競合を避けるため javadoc 検証は親が一括・逐次で実施。
  - **警告ゼロ化（クリーンアップ）**: 中央検証で検出した全件を修正 — 別パッケージへの未解決 `{@link Main}` を FQN 化（参照エラー）、`{@code ...&#42;&#42;/*.yaml}` の EscapedEntity を `<code>` 要素化（コメント終端 `*/` 回避とエンティティ解釈を両立）、暗黙 public デフォルトコンストラクタ約 40 件をクラスの実体に応じて修正（静的ユーティリティ＝private ctor、被インスタンス化型・Builder・ServiceLoader provider＝public ctor + Javadoc、抽象型＝protected）。
  - **回帰修正**: `MigrapheExtension`（Gradle managed type）のコンストラクタを誤って `protected` 化し ObjectFactory が生成不能になり TestKit 13 件が失敗 → `public` に戻して解消（Gradle は extension に public 引数なしコンストラクタを要求）。
  - **検証**: `./gradlew clean build` green、`javadoc --rerun-tasks` の警告/エラー **0**、テスト失敗 0、ErrorProne/NullAway clean、main java の日本語残存 0、全 8 モジュールで javadoc/sources jar 生成を確認。
  - **未了（Maven Central 残作業）**: POM メタデータ（name/description/url/licenses/developers/scm）と GPG 署名・OSSRH/Central Portal 連携は本セッション範囲外。

### 2026-06-25 (Session 59)
- **CLI 経路で `${VAR}` の env/sysprop 展開が効かないバグを修正（OS環境変数を `${env.VAR}` に名前空間化）**
  - **Motivation**: ユーザー報告。`password: ${PROBE_PW}` のような設定が、環境変数 `PROBE_PW` をプロセスに渡していても全て `SRCFG00011 Could not expand` で失敗していた。`${VAR:default}` だけは常に default に解決される（式評価のインラインフォールバックで ConfigSource を引かないため）という症状も一致。原因は `ConfigLoader.loadConfig` が `new SmallRyeConfigBuilder().addDefaultInterceptors()` のみを呼び、**env/sysprop の ConfigSource を一切登録していなかった**こと（式評価インターセプタはあるが展開先が無い）。コア（variables マップ ordinal 600）や `--env` プロファイルとは独立した、`ConfigLoader` の組み立ての問題だった。
  - **設計判断**: 単純な `addDefaultSources()` 追加では SmallRye の `EnvConfigSource` が環境変数名を正規化して（`TARGET_FOO` → `target.foo`）フラットな設定キー空間に直接乗せるため、`ConfigLoader.extractTargetIds`（`getPropertyNames()` を列挙して `target.` 接頭辞からターゲットIDを抽出）に `TARGET_*` 環境変数が混入するリスクがある。これを避けるため、**OS環境変数を `env.` 接頭辞に隔離**する方式を採用（ユーザー提案）。systemProperty は migraphe を起動する側が `-D` で明示的に渡す信頼入力なので `environments`/`variables` と同列に生キーのまま残す。
  - **最終的な解決順位（ordinal）**: `variables`(600, Gradle注入) > `environments/*.yaml`(500) > sysprop(400, `${VAR}`) > OS env(300, `${env.VAR}` のみ) > multi-file YAML(100)。
  - **実装（`/tdd-cycle` を 4 サイクル）**:
    1. （暫定）`addDefaultSources()` 追加 + sysprop 生キー解決のリグレッションテスト `shouldExpandSystemPropertyInYamlValue`。
    2. `System.getenv()` を `env.<NAME>` キーに詰めた `MapConfigSource(envVars, 300)` を登録 → `${env.VAR}` 解決（`shouldExpandEnvVarWithEnvPrefix`）。`MapConfigSource` に ordinal 指定コンストラクタを追加。
    3. 生キー `${VAR}` が env から解決されない（隔離）テスト `shouldNotExpandEnvVarWithBareKey`。
    4. `addDefaultSources()` を除去し、sysprop は `System.getProperties()` を `MapConfigSource(sysProps, 400)` として明示登録（`SysPropConfigSource` は依存に無かったため）。これで env 隔離と sysprop 生キー解決が両立。
  - **ドキュメント**: `docs/USER_GUIDE.md`/`.ja.md`（環境ファイル例・本番環境例・変数置換の総括記述に `${env.VAR}` 必須を明記）、`docs/ARCHITECTURE.md`（設計判断5を解決順位＋env隔離の根拠付きで詳述）、`sample/{cli,gradle}/targets/{pg,mysql}.yaml`（`${env.POSTGRES_PASSWORD:...}` / `${env.MYSQL_PASSWORD:...}` に修正）。
  - **既知の影響（破壊的変更ではないが利用側で要注意）**: env の参照構文が `${VAR}` → `${env.VAR}` に変わった（従来 CLI では env 展開自体が動いていなかったため実害は限定的）。
  - **検証**: `ConfigLoaderTest` 17件 green、`mcp__migraphe-build__run_errorprone_check`（全モジュール `clean build --warning-mode all`）exit 0。

### 2026-06-25 (Session 58)
- **環境プロファイル選択 `--env <name>` を CLI に実装（up/down/status）**
  - **Motivation**: `environments/<name>.yaml` のオーバーレイ機構はコア層（`ConfigLoader.loadConfig(baseDir, envName, variables)`）に実装済み・テスト済みだったが、CLI から環境を選択する経路が無く、`ExecutionContext.load` が常に envName=null で呼ばれていたため一切適用されなかった。`docs/USER_GUIDE.md` は `migraphe up --env production` と記載していたが実際には動かないドキュメント/実装ギャップ状態だった。
  - **実装（`/tdd-cycle` を 4 サイクル）**:
    1. `ExecutionContext.load(Path, PluginRegistry, @Nullable String envName)` および `(..., envName, Map variables)` オーバーロードを追加。内部で `configLoader.load(...)` → `configLoader.loadConfig(baseDir, envName, variables)` に切替。既存2オーバーロードは envName=null 委譲で後方互換維持。
    2. `Main.parseEnvOption(String[])`（package-private）追加。tidy で `parseNameOption` と共通化し `parseValueOption(args, flag)` を抽出。
    3. `Main.loadContext(baseDir, pluginRegistry, args)` を抽出し、`Main.run` の up/down/status 経路を `ExecutionContext.load(baseDir, pluginRegistry, parseEnvOption(args))` 配線に変更。
    4. **位置引数バグ修正**: `createUpCommand` / `createDownCommand` が `--env <value>` を位置引数（マイグレーションID/version）として誤取得していた。`firstPositionalArg(String[])` を追加し、コマンド語・値付きフラグ（`--env`/`--name`）とその値・真偽フラグ（`-y`/`--dry-run`/`--all`）を読み飛ばして最初の位置引数を返すよう修正。printUsage に `--env <name>` を追記。
  - **挙動**: `environments/<name>.yaml` が存在しなければフラグは無視（ベース設定）。`validate`/`generate` は独自ロード経路のため未対応（フォローアップ）。Gradle plugin も別途。
  - **検証**: `./gradlew build`（全モジュール、テスト + spotless + ErrorProne）成功。新規テスト: `ExecutionContextTest`（envName で target 上書きが反映）、`MainTest`（parseEnvOption / loadContext 配線 / firstPositionalArg のフラグ読み飛ばし）。

### 2026-06-25 (Session 57)
- **ドキュメント整理: プラグインの使い方を各プラグイン README に集約し、USER_GUIDE はリンク誘導に集約**
  - **方針（ユーザー合意済）**: (1) プラグインの使い方はプラグイン側 README に集約。(2) メインドキュメント（USER_GUIDE）はプラグイン一覧テーブル＋リンク＋1行説明にとどめる。(3) 各プラグイン README はそのプラグインのオプションを**すべて**列挙・説明することを必須とする。(4) 英語版・日本語版の両方を同期。
  - **各プラグイン README（英・日 計8ファイル）にオプション全列挙**: `migraphe-plugin-jdbc` / `-postgresql` / `-mysql` / `-generator-json`。**ターゲットフィールド**表（既存を補完）、**タスクフィールド**表（`name`/`description`/`target`/`dependencies`/`up`/`down`/`autocommit` を新設。`SqlTaskDefinition` 由来）、**ジェネレーターフィールド**表（`name`/`type`/`source.type`/`source.target`/`output-dir`（default `docs/schema`）/`excludes[].schema`/`excludes[].table`。`JdbcMarkdownDefinition` 由来）を新設。
  - **方言依存の挙動を各 README へ移設**: 複数文分割（PostgreSQL のドル引用符 `$$`、MySQL の再帰 `BEGIN ... END` / `DELIMITER`、汎用 JDBC の標準 `;` 分割）と Autocommit のユースケースを、USER_GUIDE から具体例ごと各プラグイン README へ移した。jdbc README には jdbc-markdown の出力構造と imported/exported 外部キーの説明も移設。
  - **`password` の必須/任意の食い違いを解消**: postgresql/mysql README が `password` を「必須」と記載していたが、コード（`PostgreSQLEnvironmentDefinition` / `MySQLEnvironmentDefinition`）は `Optional<String>`。実装に合わせて全プラグインで「任意」に統一。
  - **USER_GUIDE.md / .ja.md の整理**: ターゲットのDB別設定例・複数文分割の方言別例・ストアドプロシージャ例・Autocommit のDB固有ユースケース・generator のタイプ別フィールド詳細を削除し、概要＋プラグイン一覧テーブル（リンク+1行説明）＋各 README への誘導に置換。`generate` コマンド／source-output アーキテクチャ／プラグイン一覧などの共通基盤は維持。
  - **検証**: 外部相対リンク（`../migraphe-plugin-*/README*.md`）の解決、各 README 内アンカー（`#target-fields` / `#task-fields` / `#generator-fields` / `#autocommit-mode` および日本語版アンカー）と見出しの一致、英/日の見出し数一致、TOC に削除セクションへの dead anchor が無いことを確認。コード変更なし（ドキュメントのみ）。

### 2026-06-01 (Session 56)
- **Gradle 8.5 → 9.5.1 へアップグレード（Dependabot PR #15 の CI 失敗を解消）**
  - **JUnit Platform launcher**: Gradle 9 はテスト実行時クラスパスに JUnit Platform launcher を暗黙追加しなくなったため、`migraphe-api` などで `Failed to load JUnit Platform` で失敗していた。ルート `build.gradle.kts` の `subprojects { dependencies { ... } }` に `testRuntimeOnly(libs.junit.platform.launcher)` を一元追加し、`migraphe-core` / `migraphe-gradle-plugin` に残っていた個別宣言を削除して重複を解消。
  - **NullAway（Gradle 9 が API に null 注釈を追加）**: `MigrapheGenerateTask` で `Property.getOrElse(null)` → `getOrNull()`、`new GradleException(e.getMessage(), e)` → `String.valueOf(e.getMessage())` に修正。
  - **タスク検証の厳格化（警告 → エラー）**: Gradle 9 は入力プロパティの正規化戦略とタスクのキャッシュ可否注釈を必須化。`AbstractMigrapheTask` の `getBaseDir()` に `@PathSensitive(RELATIVE)`、`getPluginClasspath()` を `@InputFiles` → `@Classpath` に変更。`@DisableCachingByDefault` は継承されないため、5 つの具象タスク（Up/Down/Status/Validate/Generate）すべてに付与。
  - **mise**: `.mise.toml` から `gradle` 行を削除し、gradle バージョンの source of truth を Gradle Wrapper（`gradle-wrapper.properties`）に一本化した（CI/IDE/Dependabot はすべて wrapper 経由で動作するため。mise は `java` の管理に専念）。
  - **検証**: `./gradlew build`（全 956 テスト + spotless + ErrorProne）成功。CI ワークフロー（`gradle/actions/setup-gradle@v6`）は変更不要。Gradle 10 向けの前方互換 deprecation 警告は残るが 9.5.1 ビルドには影響しないためスコープ外。

### 2026-06-01 (Session 55)
- **SQL 文分割をパーサーコンビネーター方式に刷新し、方言ごとの文法を各プラグインで独自定義**
  - **Motivation**: 2 件の実害を解消するため。(1) MySQL で複数 SQL 文をデフォルト（トランザクション）モードで書くと、従来は 1 回の `Statement.execute()` にまとめて渡していたため失敗していた。(2) PostgreSQL の `DO $$ ... $$ LANGUAGE plpgsql` が autocommit モードの素朴な正規表現分割で `$$` 内の `;` により壊れていた。素朴な正規表現/文字列スキャン方式では方言ごとの字句（ドル引用符・バッククォート・`BEGIN...END` ブロック・DELIMITER）を正しく扱えないため、文法を宣言的に組めるパーサーコンビネーターへ刷新した。
  - **設計（パーサーコンビネーター）**: `migraphe-plugin-jdbc` に汎用ツールキット `io.github.kakusuke.migraphe.jdbc.statement` を新設。`SqlParser` インターフェースと `SqlParsers` のコンビネーター群（`literal`/`seq`/`or`/`anyChar`/`not`/`many`/`opt`/`keyword`/`ref`/`quoted`/`lineComment`/`delimited`/`standardRegion`/`whitespace`）で文法を宣言的に合成する。`StatementSplitter` が分割エンジン、`DelimiterDirective` が DELIMITER フックを表す。
  - **責務分担（JDBC は汎用のみ・方言は各プラグイン）**: `StatementSplitter.standard()` は文字列リテラル/識別子/`--`/`/* */` コメント内の `;` を分割せず、各セグメントを生のまま（外側 trim のみ）保持する。多文字区切り＋ DELIMITER ディレクティブフックに対応。方言固有の文法は各プラグインで独自定義する。
  - **精緻化（先頭トリビア strip の廃止）**: 当初は各セグメント先頭のトリビア（空白・コメント）を strip していたが、これを廃止。実機検証（コメントのみ／`/*!*/` 実行コメント／`/*+*/` ヒントは MySQL/PostgreSQL/H2 でエラーにならず、空白のみのみが `Query was empty`、しかも `trim()` で除外される）に基づき、先頭コメントを次の文に付随させて保持する方式へ変更した。`StatementSplitter` から `trivia` 引数を撤廃しコンストラクタを `(region, char)` / `(region, String, DelimiterDirective)` の 2 つに整理、DELIMITER 検出は先頭空白のみスキップした probe 位置で行う。これにより MySQL の `/*!...*/` 実行コメントやオプティマイザヒントが文として保持され、`--` 行コメントの改行も文内部に残る。
    - **PostgreSQL**: `PostgreSqlGrammar`（ドル引用符 `$tag$...$tag$`）。`DO $$...$$` / `CREATE FUNCTION ... $$...$$` を 1 文化し、内部 `;` で割れない。**キーワードブロックは持たない**ので `BEGIN;`/`COMMIT;` のトランザクション制御文は独立分割される（`BEGIN` をブロック開始として誤飲しない）。`PostgreSQLEnvironment.statementSplitter()` でオーバーライド。
    - **MySQL**: `MySqlGrammar`（バッククォート識別子、`#` および `-- `（空白要求）コメント、`\'`/`''` エスケープ文字列、**再帰ブロック文法** BEGIN/IF/CASE/LOOP/WHILE/REPEAT、**DELIMITER** ディレクティブ）。再帰文法によりブロック内 `;` を非分割とする挙動が自然に導かれる。`MySQLEnvironment.statementSplitter()` でオーバーライド。
  - **配線（両モードで分割ループ統一・旧 SqlStatements 削除）**: `JdbcEnvironment.statementSplitter()` を追加（既定は `StatementSplitter.standard()`）。`JdbcUpTask`/`JdbcDownTask` は autocommit/transaction **両モードとも** `environment.statementSplitter().split()` でループ実行する（transaction モードは最後に 1 回だけ commit）。旧 `SqlStatements` は削除。
  - **テスト**: 各方言文法のユニットテスト（`SqlParsersTest`/`StatementSplitterTest`/`PostgreSqlGrammarTest`/`MySqlGrammarTest`）、Testcontainers 結合テスト（`MySQLIntegrationTest`/`PostgreSQLIntegrationTest`）、CLI e2e（`UpCommandTest` に PostgreSQL の DO/複数文/FUNCTION、新規 `UpCommandMySQLTest` に MySQL の複数 CREATE TABLE/PROCEDURE/DELIMITER）。`migraphe-cli/build.gradle.kts` のテスト依存に `testImplementation(project(":migraphe-plugin-mysql"))` と testcontainers-mysql を追加（テストスコープのみ）。
  - **TDD 段取り**: micro-plan → test-writer → minimal-fix → regression-guard → tidy の `/tdd-cycle` を複数ループで進行（コンビネーター基盤 → StatementSplitter.standard → PostgreSQL ドル引用符 → MySQL 再帰ブロック/DELIMITER → JdbcUp/DownTask 配線 → 結合・CLI e2e）。

### 2026-05-29 (Session 54)
- **3 つの Executor (`MigrationExecutor` / `ParallelMigrationExecutor` / `RollbackExecutor`) を `DagExecutor` 1 つに統合**
  - **Motivation**: Session 53 の fail-soft 化で 3 つの同型ロジックを同期更新する保守税が顕在化。今後の observability / retry / hooks 追加で 3 倍コストが乗るため一本化。本質的に 3 つとも「DAG を direction + maxParallelism + ready-queue で消化する worklist」であり同一アルゴリズムの specialization。
  - **新クラス**: `DagExecutor(MigrationGraph graph, HistoryRepository history, ExecutionListener listener, ExecutionDirection direction, int maxParallelism)` (`migraphe-core/.../execution/DagExecutor.java`, ~340 行)。`Executor` interface を実装し `determineTargetNodes` / `execute` の他 DOWN 専用の `determineRollbackTargets` を提供。
  - **vthread 統一**: `maxParallelism=1` でも vthread + Semaphore(1) + `PriorityBlockingQueue` + `ReadyNodeTracker` パスを通す。`Thread.startVirtualThread` の overhead は ~50μs/task で典型 migration の 0.05% 未満 (ユーザー合意済の許容コスト)。
  - **direction 切替の集約点**: `taskFor(node)` (upTask/downTask)、`transitiveSuccessorsOf(id)` (getAllDependents/getAllDependencies)、`createPlanFor(set)` (createExecutionPlanFor/createReverseExecutionPlanFor)、`recordSuccess` (upSuccess/downSuccess)、`isAlreadyInRequiredState` (UP: 既実行スキップ "already executed" / DOWN: 未実行スキップ "not executed") の 5 ヘルパーに集約。
  - **`ReadyNodeTracker` を direction-aware に拡張**: 3 引数コンストラクタ `(graph, targetNodes, direction)` を新設し、UP では既存通り `getDependencies`/`getDependents`、DOWN では逆転 — `getDependents`/`getDependencies` を使う `predecessors` / `successors` 2 つの private helper にロジックを集約。
  - **Sync ラッパー常時化**: `DagExecutor` のコンストラクタが内部で `SynchronizedHistoryRepository` / `SynchronizedExecutionListener` を `instanceof` チェック付きで自動装着 (二重ラップ回避)。consumer 側 (`UpCommand` / `MigrapheUpTask`) で if-分岐 + 手動 wrap が消えた。
  - **consumer 4 ファイル差し替え** (`migraphe-cli` / `migraphe-gradle-plugin`): UP 系は `int maxParallelism = execConfig.parallel() ? execConfig.maxParallelism() : 1; return new DagExecutor(..., ExecutionDirection.UP, maxParallelism);` の 2 行に縮退。DOWN 系は `new DagExecutor(..., ExecutionDirection.DOWN, 1)` で構築し `determineRollbackTargets` を直接呼ぶ。合計 +24/-32 行。
  - **削除**: `MigrationExecutor.java` / `ParallelMigrationExecutor.java` / `RollbackExecutor.java` の本体 3 + 対応する旧テスト 3 = 6 ファイル削除。
  - **テスト**: 旧 3 テストファイル (1,394 行) を `DagExecutorSequentialUpTest` (10 件) / `DagExecutorParallelUpTest` (12 件) / `DagExecutorRollbackTest` (10 件) へリネーム + 移植、`MockExecutionListener` を `core/execution/support/MockExecutionListener.java` に共通化 (6 重複 → 1)。`ReadyNodeTrackerTest` に DOWN initial-ready / DOWN markCompleted 2 件を追加。全 1,400+ テスト 100% 緑。
  - **副産物**: DOWN parallel が自然にサポート対象 (現状は `execution.parallel` は UP-only のセマンティクスを維持、設定 expose は将来課題)。
  - **TDD 段取り**: micro-plan → test-writer → minimal-fix → regression-guard → tidy の `/tdd-cycle` を 7+ ループで進行 (ReadyNodeTracker direction / DagExecutor 骨格 / UP happy path / parallel UP / DOWN rollback / determineRollbackTargets / 共通化 / 残テスト一括移植)。
  - **Branch 戦略**: `origin/main` から `refactor/dag-executor-unification` を切って作業。

### 2026-05-28 (Session 53)
- **Executor を fail-fast から fail-soft へ統一: 失敗時も独立タスクは完走させて rerun の冪等性を保つ**
  - **Motivation**: ユーザーから「up / down を並列に実行し、どれかが止まると `failureDetected` で dispatch 即停止 → in-flight は完走するが queue 上のノードはドロップ → rerun したときに『最初から成功実行した場合に流れたはずのタスク』とは違う tasks が流れる ⇒ idempotency が壊れている」という指摘。原因は 3 executor の fail-fast 設計に共通する idempotency hole。
  - **採用したセマンティクス (fail-soft / `make -k` 相当)**: 失敗ノードはその場で failure record を残しつつ、**失敗ノードに (推移的に) 依存しないタスクは引き続き実行**。失敗ノードの依存ツリーに属するタスクは `onNodeSkipped` で reason=`"dependency failed: <id>"` 通知 + skippedCount に加算。すべての実行可能タスクが終了したのち、failure が 1 件でもあれば全体結果は `failure`。
  - **適用範囲**: ParallelMigrationExecutor (並列 UP) / MigrationExecutor (直列 UP) / RollbackExecutor (直列 DOWN) の 3 つ全て。デフォルト挙動として実装、config フラグや CLI フラグでの切り替えは設けない (ユーザー合意)。
  - **実装ポイント**:
    - **直列 UP / DOWN**: `Set<NodeId> failedNodes` を loop scope で持ち、各ノード処理前に `findFailedDependency` / `findFailedDownDependency` で依存先 (UP は `graph.getDependencies`, DOWN は `graph.getDependents`) が `failedNodes` に含まれていないかチェック。該当すれば skip 通知 + `failedNodes` 追加 + `continue`。失敗パスから `return` を削除し、ループ完了後に `failedNodes.isEmpty()` で結果分岐。トポロジカル順序で逐次処理するため、推移伝播は自動成立 (親が failedNodes に入れば子も自動的に検知)。
    - **並列 UP**: `failureDetected.get() → drop` 短絡を削除。代わりに `Set<NodeId> failedNodes = ConcurrentHashMap.newKeySet()` を導入し、失敗 vthread の中で `graph.getAllDependents(failedId)` で推移的子集合を計算 → 各子について `failedNodes.add()` CAS で勝った場合のみ skip 通知 + `latch.countDown()`。失敗ノードは `processCompletion(tracker.markCompleted)` を呼ばない (= 子の inDegree は 0 にならず readyQueue に投入されない) ことで、失敗ノードの子が dispatch されないことを保証。
    - **並列ループ**: `for (i = 0..totalNodes) readyQueue.take()` を `while (latch.getCount() > 0) readyQueue.poll(100ms)` に書き換え。失敗伝播で latch を直接減算するため、メインループは失敗ノードの子を queue から待つ必要がない (poll の null は無視して次回)。Race 保険として dispatch 直前に `failedNodes.contains(node.id())` をチェックして二重処理を防ぐ。
    - **`skippedCount` を AtomicInteger 化** (並列のみ): 失敗伝播は vthread 内で発生するため thread-safe な加算が必要。
  - **`ReadyNodeTracker` は変更不要**: 失敗ノードを `markCompleted` しない方針なので、子の inDegree が 0 にならず自動的に dispatch されない仕組み。トラッカー API は触らずに済んだ。
  - **テスト**: 7 TDD cycle で進行。
    - 直列 UP: `shouldContinueExecutingIndependentNodesAfterFailure` (A 失敗時に独立な B が完走), `shouldSkipTransitiveDependentsWithReasonOnFailure` (A→B→C で A 失敗時に B, C が `"dependency failed: a"` / `"dependency failed: b"` で skip)
    - 並列 UP: `shouldSkipDependentsOnFailure` (旧 `shouldNotExecuteDependentsOnFailure` を fail-soft 化), `shouldContinueIndependentNodesAfterFailure`, `shouldSkipAllDependentsOnFailure` (A→B, A→C で兄弟独立な B, C 両方 skip), `shouldSkipMultiDepNodeIfAnyParentFails` (A→C, B→C で A 失敗 + B 成功 → C は A 失敗のため skip)
    - DOWN: `shouldContinueIndependentNodesAfterDownFailure`, `shouldSkipUpstreamOnDownFailure` (DOWN 方向で B 失敗時に「B の DOWN を待っていた」UP 親 A が skip)
    - `MockExecutionListener` 3 ファイルとも `Map<NodeId, String> skipReasons` を追加して reason を assert できるように。
  - **既存テストの handling**: `shouldNotExecuteDependentsOnFailure` は名前と意図を `shouldSkipDependentsOnFailure` に更新し、`listener.skippedNodes` + `skipReasons` の追加 assert を入れた。「dependent は実行されない」という不変条件は変わらないため、回帰は出なかった。
  - **ドキュメント更新**: `docs/USER_GUIDE.{md,ja.md}` の並列実行セクションの "fail-fast" 記述を fail-soft + 冪等性の説明に置換。`CLAUDE.md` の Design Decision 13 (Parallel Execution) も "Fail-fast" → "Fail-soft (revised Session 53)" に更新。
  - **アウトオブスコープ**: failed node の **ロールバック** (例: 失敗時に in-flight の成功分を auto-revert する) は対象外。ユーザーは引き続き手動 down or 修正 + rerun で対処。冪等性が保たれていれば rerun で過剰実行は起きない。
  - **Sample E2E**: 計画中だがメイン spec 確認は unit test で済んだため未実施。実機 DB で意図的失敗を仕込んでの確認は次セッションでも可。
  - Tests: 全モジュール 100% passing (`./gradlew test` で全 task UP-TO-DATE / spotless / ErrorProne クリーン)。`migraphe-plugin-mysql:test` の Testcontainers 並列起動干渉と思われる flake は単独再実行で解消。7 micro TDD cycle で進めた。

### 2026-05-28 (Session 52)
- **`project.scan-root`: tasks/targets/environments/plugins の親ディレクトリを `migraphe.yaml` 直下から切り離せるように**
  - **Motivation**: ユーザーから「`migraphe.yaml` 本体は repo ルートに置きつつ、`tasks/`, `targets/`, `environments/`, `plugins/` をサブディレクトリにまとめたい」という要望。これまでは `migraphe.yaml` の親 (`baseDir`) 直下に全部置く前提だった。
  - **設計**: `migraphe.yaml` の `project.scan-root` (Optional<String>) を 1 つ追加すれば、その値が tasks/targets/environments/plugins の探索起点を一括で切り替える。値は `migraphe.yaml` の親ディレクトリ起点の相対パス、もしくは絶対パス。未指定なら従来通り `baseDir` と同じ (= 完全な後方互換)。**命名は TypeScript の `rootDir` 由来ではなく Liquibase の `searchPath` 系の "scan の起点" を直接表現する `scan-root` を採用** — ユーザーが `config-dir` / `root-dir` 両案にしっくり来ず、Liquibase / Flyway / Cargo の慣習を見直した上で再選択した経緯あり。
  - **CLI と Gradle で挙動を完全一致させる**: 両方とも同じ `migraphe.yaml` フィールド経由でのみ指定 (Gradle DSL に新規プロパティは追加しない)。`MigrapheExtension.baseDir` は引き続き `migraphe.yaml` の親、`ConfigLoader` 内部で `scan-root` を解決する。
  - **実装**:
    - `ProjectConfig.ProjectSection.scanRoot()` (Optional<String>) を追加
    - `ConfigLoader.resolveScanRoot(baseDir, projectConfigFile)` を private helper として導入し、`loadConfig` 内で `scanRoot` を解決。targets/tasks/environments の `YamlFileScanner` 呼出 + `TaskIdGenerator.generateTaskId` の baseDir を全て scanRoot に統一
    - `ConfigLoader.resolveScanRoot(baseDir)` を public ラッパーとして公開 (Gradle plugin / `ExecutionContext` で再利用するため)
    - `ExecutionContext` record に `Path scanRoot` を追加し、`load()` factory で計算
    - `PluginConfigPreParser` (SnakeYAML 直読み) でも `project.scan-root` を pre-parse し、`PluginConfigParseResult.scanRoot()` で取り出せるように
    - CLI `Main.resolvePluginsDir(baseDir, parsed)` を新設し、`initializePluginRegistry` と `GenerateCommand` の plugins ディレクトリ参照を `scanRoot.resolve("plugins")` に統一
    - Gradle plugin `MigrapheGenerateTask` の `context.baseDir().resolve("plugins")` を `context.scanRoot().resolve("plugins")` に差し替え
  - **`migraphe.yaml` の二重 parse は許容**: scan-root を取り出すために `ConfigLoader.loadConfig` 内で projectConfig 限定の SmallRyeConfig を組む。`ExecutionContext.load` でも `resolveScanRoot` を再度呼ぶ。性能影響軽微で、`scan-root` キーに `${VAR}` を含める用途は想定しない (= 変数展開なしで取り出す現方式で十分)。
  - **適用対象外**: `migraphe.yaml` 本体, `migraphe.lock.yaml`, `plugins:` / `repositories:` セクション (これらは `migraphe.yaml` 内のキーなので scan-root の影響を受けない)。`generators` の `outputDir` も現状 baseDir 相対のままで scope out (将来必要なら別途設計)。
  - **テスト**: `ConfigLoaderTest` に 4 件 (scanRoot 未指定 / `scan-root: subdir` の targets / tasks / environments / 絶対パス)、`PluginConfigPreParserTest` に 1 件、`MainTest` に 2 件 (Optional あり/なし)、`ExecutionContextTest` に 1 件 (`shouldResolveScanRootFromProjectConfig`)、`MigrapheValidateTaskFunctionalTest` に 1 件 (Gradle TestKit + scan-root レイアウト) を追加。`MigrapheGenerateTaskFunctionalTest` で `subdir/plugins/` 経由でプラグインを発見できるかを直接検証する案は、Jackson 等の transitive 依存を手動コピーする必要があり TestKit では現実的でないため断念 → 代わりに `ExecutionContext.scanRoot()` の core unit test で保証する形に整理した。
  - **`PluginConfigParseResult` のシグネチャ変更**: record に第 3 コンポーネント `Optional<String> scanRoot` を追加し、既存テスト 6 箇所のコンストラクタ呼出を `, Optional.empty()` 追記で更新。compact constructor で null guard あり。
  - **E2E で発見した bug 2 件 (修正済み)**:
    - `ConfigValidator.validate(baseDir)` が `scanner.scanTargetFiles(baseDir)` / `scanner.scanTaskFiles(baseDir)` をハードコードしていたため、`migraphe validate` (CLI) / `migrapheValidate` (Gradle) で `scan-root` 配下の tasks/targets を 0 件と認識していた。`new ConfigLoader().resolveScanRoot(baseDir)` で解決した scanRoot を渡すよう修正。`ConfigValidatorTest.shouldValidateUsingScanRootForTargetsAndTasks` で回帰ガード。
    - CLI の `ValidateCommand.displayCheckResults` で「Checking targets (X files)」の件数表示が `scanner.scanTargetFiles(baseDir)` を使っており、ConfigValidator が修正されても表示だけが 0 のままだった。同じく scanRoot 経由に修正。
  - **Sample プロジェクトでの E2E 確認 (実施済み)**: `sample/cli` と `sample/gradle` を `/tmp` にコピーして `scan-root: config` レイアウトに移行し、`migraphe validate` と `./gradlew migrapheValidate` 両方で `targets 2 / tasks 19 / Validation successful.` を確認。Sample 本体は無変更。
  - Tests: 全モジュール 100% passing. `./gradlew clean build --warning-mode all` で警告ゼロ、Spotless / ErrorProne クリーン。10+ micro TDD cycles で進めた。

### 2026-05-25 (Session 51)
- **リリースアーカイブをフラット化 (PR #29) + バージョン 0.3.0 への bump + リリース手順の明文化**
  - **Archive flatten**: `migraphe-cli/build.gradle.kts` の `distTar` / `distZip` の `eachFile` を「トップ階層を `migraphe` に置換」から「トップ階層 (`migraphe-<version>/`) を丸ごと除去」に変更 (`replaceFirst(Regex("^migraphe-[^/]+/"), "")`)。結果アーカイブは `bin/` `lib/` がルート直下に並ぶ。これで mise の github バックエンドが追加オプション無しで `mise use github:kakusuke/migraphe` で取り込める (github バックエンドは展開ルート直下の `bin/` を優先探索し、起動スクリプトの `../lib` 参照も同ルートで解決)。実ビルド + 展開 + `bin/migraphe --version` で動作確認済み。
  - **Breaking note**: 素の `curl ... | tar xz` がカレントに `bin/` `lib/` を直接展開するようになった (旧: `migraphe/` ディレクトリ作成)。`README*.md` / `docs/USER_GUIDE*.md` の手動インストール手順を展開先ディレクトリ指定 (`tar xz -C` / `unzip -d`) + mise 推奨に書き換え。
  - **Version bump 0.2.1 → 0.3.0**: 0.x の破壊的変更は MINOR を上げる慣習 (Cargo/npm の `^` 互換境界) に従った。SemVer 2.0.0 §4 は `0.x` で「anything MAY change」とするのみで増分ルールは規定しないため、これは spec 要求ではなく慣習。`gradle.properties` + 全 docs/sample の `0.2.1`/`v0.2.1` を一括置換。
  - **Release procedure 明文化**: バージョン bump 時に `gradle.properties` を忘れがちな問題に対し、`CONTRIBUTING.md` に "Release procedure" セクションを新設 (gradle.properties が canonical、docs/sample は cosmetic、tag push で `release.yml` 起動) + 0.x の MINOR 扱いの注記を追加。`CLAUDE.md` の Session End Procedure にも version bump 時の `gradle.properties` 注意を追記。
  - **Doc/config-only change**: 本番 Java コードは無変更。
