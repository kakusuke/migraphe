# ExecutionGraphView 再設計 - 支配木ベース描画

## 基本コンセプト

現在のインライン分岐+マージレーン方式を廃止し、**支配木 (Dominator Tree)** ベースの描画に切り替える。

- **トポロジカルソート (DFS)** でノードを並べる
- 縦方向に **支配木** を描画する
- (今後) 描画されていない辺（非支配木辺）を追加描画する

## 支配木とは

ノード D がノード N を**支配する** = ルートから N への全てのパスが D を通る。
**直接支配者 (idom)** = N を支配するノードのうち、N に最も近いもの。

DAG の各ノードに対して idom を求めると木構造（支配木）が得られる。

### 例: ダイヤモンド A→{B,C}→D

- idom(B) = A, idom(C) = A
- idom(D) = A (B経由でもC経由でもDに到達可能なので、BもCもDを支配しない)
- 支配木: A→{B, C, D}

### 例: ネストダイヤモンド A→{B→{F,G}→H, C}→E

- idom(B) = A, idom(C) = A
- idom(F) = B, idom(G) = B, idom(H) = B (F,G共にBの子なので LCA(F,G)=B)
- idom(E) = A (H経由とC経由があるので、LCA(H,C)=A)
- 支配木: A→{B→{F, G, H}, C, E}

## 描画パターン

### (1) 直列型（チェーン）

支配木の子が1つだけ → trunk（同一カラム）として描画。ノード間に `│` コネクタ。

```
●
│
●
│
●
```

### (2) 分岐型（フォーク）

支配木の子が複数 → 1つを trunk（同一カラム）、残りを branch（右にインデント）として描画。

```
●         A (col 0)
├─●       B (col 1, branch)
├─●       C (col 1, branch)
●         D (col 0, trunk child)
```

- branch は `├─●` でインライン表示
- trunk child は同じカラムで `●` として表示
- `├` の縦ストロークが trunk の継続を表す

### (3) ネスト型（分岐の入れ子）

branch の subtree を再帰的に描画。サブツリー完了後に次の branch/trunk。

```
●           A (col 0)
├─●         B (col 1, branch of A)
│ ├─●       X (col 2, branch of B)
│ ├─●       Y (col 2, branch of B)
│ ●         Z (col 1, trunk child of B)
├─●         C (col 1, branch of A)
●           D (col 0, trunk child of A)
```

- `│` at col 0 = A の trunk が継続中
- B の subtree (X, Y, Z) が完全に描画されてから C に移る

### (4) 直列+分岐

チェーンの途中からフォークが発生するケース。

```
●         A (col 0)
│
●         B (col 0, trunk child of A)
├─●       C (col 1, branch of B)
●         D (col 0, trunk child of B)
```

## Trunk 選択ヒューリスティック

支配木の各ノードの子の中から1つを trunk（同一カラム継続）として選ぶ。

**選択基準**: 支配木サブツリーが最も深い子を trunk とする。
タイブレーク: トポロジカル順で最後に出現する子。

理由:
- trunk に深いサブツリーを配置 → branch は浅い → 全体幅が最小化
- ネストダイヤモンドで内側ダイヤモンドが trunk 側に入り、カラム再利用可能

## アルゴリズム

### Step 1: 支配木の構築

1. DAG をトポロジカルソート
2. 各ノードの idom を計算:
   - 親が1つ: idom(N) = parent
   - 親が複数: idom(N) = LCA(parents) in dominator tree
3. 複数ルートの場合: 仮想スーパールートを導入（描画しない）

**LCA 計算**: 各親から支配木を root まで遡り、最初に共通する祖先を見つける。

### Step 2: Trunk 選択

支配木の各ノードについて:
1. 子が0個: leaf（何もしない）
2. 子が1個: その子が trunk
3. 子が複数: サブツリー深度が最大の子を trunk、残りは branch

### Step 3: DFS レンダリング順序決定

支配木を DFS で走査:
```
order(node):
  emit(node)
  branches = domChildren(node) - {trunkChild}
  for branch in branches:
    order(branch)  // branch subtree を先に
  if trunkChild exists:
    order(trunkChild)  // trunk は最後
```

### Step 4: カラム割り当て

- Root: column = 0
- Trunk child: column = parent の column
- Branch child: column = parent の column + 1

### Step 5: ASCII レンダリング

各ノードに対してグラフプレフィックスを生成。

**Active columns**: 祖先の trunk が通過中のカラムを追跡。

#### Branch ノード (col C, parent col C-1):
```
col 0..C-2: │ (active) or   (inactive)
col C-1:    ├─ (more branches/trunk after) or └─ (never — trunk always follows)
col C:      ●
col C+1..:  │ (active) or   (inactive)
```

#### Trunk ノード (col C, same as parent):
```
col 0..C-1: │ (active) or   (inactive)
col C:      ●
col C+1..:  │ (active) or   (inactive)
```

#### チェーンコネクタ (parent has only trunk child):
```
col 0..C-1: │ (active) or   (inactive)
col C:      │
col C+1..:  │ (active) or   (inactive)
```

#### カラム幅
各カラム = 2文字。最後のカラムは 1文字（● のみ）。全行を最大幅にパディング。

## テストケース期待値

### 1. 単一ノード
```
● A
```
Dom tree: A (root, no children)

### 2. 直列 A→B→C
```
● A
│
● B
│
● C
```
Dom tree: A→B→C (all trunk)

### 3. 分岐 A→{B,C} (leaf fork)
```
●   A
├─● B
●   C
```
Dom tree: A→{B, C}. Trunk = C (last in topo). B = branch.
Note: `├` at col 0 because trunk C follows.

### 4. ダイヤモンド A→{B,C}→D
```
●   A
├─● B
├─● C
●   D
```
Dom tree: A→{B, C, D}. Trunk = D. B, C = branches.

### 5. 3分岐 A→{B,C,D} (leaves)
```
●   A
├─● B
├─● C
●   D
```
Dom tree: A→{B, C, D}. Trunk = D.

### 6. 連続ダイヤモンド A→{B,C}→D→{E,F}→G
```
●   A
├─● B
├─● C
●   D
├─● E
├─● F
●   G
```
Dom tree: A→{B, C, D→{E, F, G}}.
Trunk path: A→D→G. B,C branches of A. E,F branches of D.

### 7. ネストダイヤモンド A→{B→{F,G}→H, C}→E
```
●       A
├─●     B
│ ├─● F
│ ├─● G
│ ●     H
├─●     C
●       E
```
Dom tree: A→{B→{F,G,H}, C, E}. Trunk path: A→E. B subtree: trunk H.
Width = 3 columns.

### 8. 分岐+チェーン A→B, A→C→D
```
●   A
├─● B
●   C
│
●   D
```
Dom tree: A→{B, C→D}. Trunk = C (deeper subtree).

### 9. 並列独立チェーン A→B, C→D
```
● A
│
● B

● C
│
● D
```
2つの独立サブグラフ。空行で分離。

### 10. 複数独立ルート A, B, C
```
● A
● B
● C
```
子のない独立ルート同士は空行なし（visible structure なし）。

### 11. 深いネスト 5段チェーン A→B→C→D→E
```
● A
│
● B
│
● C
│
● D
│
● E
```

### 12. クロス依存 A,B→C,D
現行のクロス依存特殊レンダリングは廃止。支配木で処理:
- 仮想ルート→{A, B, C, D}
- Trunk = D, branches = A, B, C

```
● A
● B
● C
● D
```
（全てが仮想ルートの直接の子 → 独立ルート扱い。非支配木辺は今後対応。）

### 13. 部分マージ A→{B,C,D}, {B,C}→E
Dom tree: A→{B,C,D,E}. idom(E) = A (B経由もC経由も可).
```
●   A
├─● B
├─● C
├─● D
●   E
```

### 14. サブグラフ分離: 001→{002,003,005}, {002,003}→004te, 003→004ed, 101独立
Dom tree: 001→{002, 003→004ed, 005, 004te}, 101 (independent).
idom(004te) = 001 (002経由も003経由もあるので LCA(002,003)=001).
idom(004ed) = 003 (single parent).
Trunk of 001 = 003 (deepest subtree: 003→004ed). Or 004te (last in topo)? TBD.

### 15. 並列チェーン後マージ A→B→C, D→E→F, {C,F}→G
```
● A
│
● B
│
● C

● D
│
● E
│
● F
│
● G
```
仮想ルート→{A→B→C, D→E→F→G}? idom(G) = D or F?
実際: G の親は C と F。C の支配木パスは A→B→C、F のパスは D→E→F。
LCA(C, F) = 仮想ルート。なので idom(G) = 仮想ルート。
Dom tree: 仮想ルート→{A→B→C, D→E→F, G}
これだと G が独立ノードのように見える。非支配木辺で後から C→G, F→G を示す必要あり。

## NodeLineInfo の変更

```java
public record NodeLineInfo(MigrationNode node, int column) {}
```

- `column`: 支配木に基づく実際のカラム値（0 = trunk, 1+ = branch depth）

## 実装ファイル

1. `ExecutionGraphView.java` - 全面書き換え
   - 支配木構築
   - Trunk 選択
   - DFS レンダリング
   - ASCII プレフィックス生成
2. `ExecutionGraphViewTest.java` - テスト期待値の更新
3. `NodeLineInfo.java` - 変更なし

## Public API（変更なし）

```java
public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed)
public List<NodeLineInfo> lines()
public List<String> renderLines(Function<MigrationNode, String> labelFn)
public String toString()
```

## 削除する機能

- `NodeClassification` record（13フィールド）
- `classifyNode()` メソッド（200行+）
- `buildNodeLine()`, `buildChainConnector()`, `buildMergeConnector()`
- `CrossDependencyInfo`, `detectCrossDependency()`, `renderCrossDependency()`
- `reorderForVisualization()`, `reorderWithinSubgraph()`
- `closedForks` 追跡ロジック

## 新しい内部構造（概要）

```java
// 支配木
Map<NodeId, NodeId> idom;           // immediate dominator
Map<NodeId, List<NodeId>> domChildren; // dominator tree children

// Trunk 選択
Set<NodeId> trunkNodes;             // trunk path nodes
Map<NodeId, NodeId> trunkChild;     // node → its trunk child

// レンダリング
List<RenderRow> renderOrder;        // DFS order of dom tree
Map<NodeId, Integer> columnOf;      // column assignment
```

## 実装順序

1. 支配木構築 + テスト
2. Trunk 選択 + テスト
3. DFS レンダリング順序 + テスト
4. ASCII レンダリング + テスト（基本パターン: 単一、チェーン、フォーク）
5. 複雑パターンのテスト更新（ダイヤモンド、ネスト、並列チェーン等）
6. クロス依存特殊処理の削除
7. 既存テストの期待値更新

## 検証

```bash
./gradlew test
./gradlew spotlessApply
```

全テスト通過 + フォーマット適用を確認。
