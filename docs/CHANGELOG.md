# Migraphe Development Changelog

Claude session records. Newest entries first. The latest session summary also lives in [CLAUDE.md](../CLAUDE.md); full history is kept here.

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
