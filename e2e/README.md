# E2E Tests — Fail-soft Executor

Session 53 で導入した fail-soft セマンティクス (失敗ノードに依存しない independent タスクは引き続き実行される) を、実 CLI + 実 DB (H2) で end-to-end 検証するためのセットアップ。

Unit test での仕様確認に加え、`migraphe up` / `migraphe down` が実際にユーザーが望む形で動くか確認できる。再現可能な手順で構成しているため、回帰確認用にも再利用できる。

## 構成

```
e2e/
├── README.md              # 本ファイル
├── migraphe.sh            # CLI ラッパー (CLI distribution の lib/* を全部 classpath にして起動)
└── parallel-fail-soft/    # シナリオ用 migraphe project (H2 file DB)
    ├── migraphe.yaml      # execution.parallel: true
    ├── targets/h2.yaml    # type: jdbc, driver=H2
    ├── tasks/
    │   ├── 001_table_a.yaml   # 独立, success
    │   ├── 002_table_b.yaml   # 独立, success
    │   ├── 003_index_a.yaml   # 001 依存, **初期状態は UP が壊れている**
    │   └── 004_index_b.yaml   # 002 依存, success
    └── data/              # H2 file (gitignore)
```

依存関係:

```
001 ──► 003 (broken)
002 ──► 004
```

## 前提

CLI distribution に JDBC plugin + H2 driver を追加した状態が必要。**初回のみ** 以下を実行:

```sh
# プロジェクトルートで
./gradlew :migraphe-cli:installDist :migraphe-plugin-jdbc:jar
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.3.232/*/h2-2.3.232.jar \
   migraphe-cli/build/install/migraphe/lib/
cp migraphe-plugin-jdbc/build/libs/migraphe-plugin-jdbc-*.jar \
   migraphe-cli/build/install/migraphe/lib/
```

`e2e/migraphe.sh` は標準の `bin/migraphe` start script ではなく `java -cp "$APP_HOME/lib/*" io.github.kakusuke.migraphe.cli.Main` で起動する。標準スクリプトの `CLASSPATH=` は明示列挙のため、後から lib/ に放り込んだ H2 / JDBC plugin を見ないため。

## シナリオ A: Parallel UP fail-soft + idempotent rerun

ユーザー報告の本丸: 「並列 UP 中にどれかが止まると、ある時点で in-flight だった独立タスクは完走するが、rerun で『別のタスクが流れ』てしまう」現象が、fail-soft 化によって解消されていることを確認する。

### 手順

```sh
cd e2e/parallel-fail-soft

# Step 1: 初期状態で UP — 003 が壊れているので失敗するはず
../migraphe.sh up -y
```

**期待出力:**

```
[OK]    001_table_a - Create table_a
[OK]    002_table_b - Create table_b
[OK]    004_index_b - Create index on table_b ...
[FAIL]  003_index_a - Create index on table_a (intentionally broken; ...)
```

注目点: **004 が走っている**。fail-fast の旧実装では 003 失敗で dispatch 即停止のため 004 はドロップされていたはず。

```sh
# Step 2: 状態確認
../migraphe.sh status
```

期待: 001 / 002 / 004 が `[✓]`、003 のみ `[ ]` pending。

```sh
# Step 3: 003 を修正
cat > tasks/003_index_a.yaml <<'YAML'
name: Create index on table_a (fixed; depends on 001)
target: h2
autocommit: true
dependencies:
  - 001_table_a
up: |
  CREATE INDEX idx_a_name ON table_a (name);
down: |
  DROP INDEX IF EXISTS idx_a_name;
YAML

# Step 4: rerun
../migraphe.sh up -y
```

**期待出力:**

```
[OK]    003_index_a - Create index on table_a (fixed; ...)

Migration completed successfully. 1 migration executed.
```

注目点: **走ったのは 003 のみ**。001/002/004 は既に historyRepository に記録済みのため skip された。これが idempotency。

```sh
# Step 5: 完了確認
../migraphe.sh status
# 全 4 件 [✓]
```

### 検証ポイント

| 観察項目 | 期待 |
|---------|------|
| 第 1 ラン: 003 と独立な 004 の実行 | `[OK]` で完走する |
| 第 1 ラン: 003 自身 | `[FAIL]` で失敗記録 |
| 第 1 ラン後の `status` | 001/002/004 が executed、003 が pending |
| 第 2 ラン: 走るタスク数 | 003 のみ (1 件) |
| 第 2 ラン後の `status` | 全 4 件 executed |

## シナリオ B: Sequential DOWN fail-soft

DOWN 方向でも同じセマンティクスが効くことを確認。シナリオ A の最後の状態 (全 4 件 UP 済) から続けて実行。

### 手順

```sh
cd e2e/parallel-fail-soft

# Step 1: 003 の DOWN を意図的に壊す (UP は fixed のままにしておく)
cat > tasks/003_index_a.yaml <<'YAML'
name: Create index on table_a (UP fixed; DOWN intentionally broken; depends on 001)
target: h2
autocommit: true
dependencies:
  - 001_table_a
up: |
  CREATE INDEX idx_a_name ON table_a (name);
down: |
  DROP INDEX nonexistent_index_oops;
YAML

# Step 2: DOWN 実行
../migraphe.sh down --all -y
```

**期待出力:**

```
[FAIL]  003_index_a - Create index on table_a (UP fixed; DOWN intentionally broken; ...)
[OK]    004_index_b - Create index on table_b (depends on 002, independent of 003 failure cone)
[SKIP]  001_table_a - Create table_a (dependency failed: 003_index_a)
[OK]    002_table_b - Create table_b
```

注目点:

- `[OK] 004_index_b`: 003 の failure cone と無関係 (002 依存) なので DOWN 完走
- `[SKIP] 001_table_a`: 「001 の DOWN は 003 の DOWN を待っていたが 003 が DOWN 失敗したため skip」を **`dependency failed: 003_index_a` の reason で明示**
- `[OK] 002_table_b`: 004 の DOWN が成功したので 002 も DOWN 完走

```sh
# Step 3: 003 を完全 fix
cat > tasks/003_index_a.yaml <<'YAML'
name: Create index on table_a (fully fixed; depends on 001)
target: h2
autocommit: true
dependencies:
  - 001_table_a
up: |
  CREATE INDEX idx_a_name ON table_a (name);
down: |
  DROP INDEX IF EXISTS idx_a_name;
YAML

# Step 4: 残り (001 のみ) を DOWN
../migraphe.sh down --all -y
```

注意: 003 の前回の DOWN failure record が history に残っているため、`determineRollbackTargets` 上は 003 は「実行済みではない」と判定され、rerun の対象は 001 のみになる。これは現行の history モデルの仕様であり、fail-soft とは別の話。

### 検証ポイント

| 観察項目 | 期待 |
|---------|------|
| 003 が DOWN 失敗したとき、独立な 004 の DOWN | `[OK]` で完走 |
| 003 が DOWN 失敗したとき、003 の DOWN を待っていた 001 | `[SKIP]` + reason `"dependency failed: 003_index_a"` |
| 独立な 002 の DOWN | `[OK]` で完走 |

## リセット手順

複数回ループしてテストする場合:

```sh
cd e2e/parallel-fail-soft
rm -f data/*.db data/*.trace.db   # H2 file をクリア
git checkout tasks/003_index_a.yaml   # 003 を初期 broken 状態に戻す
```

## 実機実行ログ (Session 53 で確認済)

シナリオ A 第 1 ラン:

```
[OK]    001_table_a - Create table_a (10ms)
[OK]    002_table_b - Create table_b (10ms)
[OK]    004_index_b - Create index on table_b (depends on 002, ...) (1ms)
[FAIL]  003_index_a - Create index on table_a (intentionally broken; ...)
   Error: Column "NONEXISTENT_COLUMN" not found ... [42122-232]
```

シナリオ A 第 2 ラン (003 fix 後):

```
Migrations to execute:
● [ ] 003_index_a - Create index on table_a (fixed; ...)
1 migration will be executed.
[OK]    003_index_a - Create index on table_a (fixed; ...) (6ms)
Migration completed successfully. 1 migration executed.
```

シナリオ B (DOWN with 003 broken):

```
[FAIL]  003_index_a - Create index on table_a (UP fixed; DOWN intentionally broken; ...)
   Error: Index "NONEXISTENT_INDEX_OOPS" not found ... [42112-232]
[OK]    004_index_b - Create index on table_b (depends on 002, ...) (5ms)
[SKIP]  001_table_a - Create table_a (dependency failed: 003_index_a)
[OK]    002_table_b - Create table_b (2ms)
```

すべて期待通り fail-soft で動作。Unit test (`MigrationExecutorTest` / `ParallelMigrationExecutorTest` / `RollbackExecutorTest`) と一貫した挙動が実機でも確認できた。
