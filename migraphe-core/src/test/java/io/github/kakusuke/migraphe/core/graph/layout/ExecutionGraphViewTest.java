package io.github.kakusuke.migraphe.core.graph.layout;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionGraphView")
class ExecutionGraphViewTest {

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        @DisplayName("単一ノードを ● id - name 形式で出力する")
        void shouldRenderSingleNode() {
            MigrationNode nodeA = node("a").name("Create users table").build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text).isEqualTo("● a - Create users table\n");
        }

        @Test
        @DisplayName("複数ノードを順番に出力する")
        void shouldRenderMultipleNodesInOrder() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text)
                    .isEqualTo(
                            """
                            ● a - Node A
                            │
                            ● b - Node B
                            │
                            ● c - Node C
                            """);
        }

        @Test
        @DisplayName("ノード ID と名前を含む")
        void shouldContainIdAndName() {
            MigrationNode nodeA = node("migration-001").name("Create users table").build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text).contains("migration-001");
            assertThat(text).contains("Create users table");
        }
    }

    @Nested
    @DisplayName("renderLines()")
    class RenderLinesTest {

        @Test
        @DisplayName("ラベル関数を各ノードに適用した結果を返す")
        void shouldApplyLabelFunctionToEachNode() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<String> lines = view.renderLines(n -> n.id().value() + ":" + n.name());

            assertThat(lines).containsExactly("● a:Node A", "│", "● b:Node B");
        }

        @Test
        @DisplayName("単一ノードで正しく動作する")
        void shouldWorkWithSingleNode() {
            MigrationNode nodeA = node("x").name("X Node").build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<String> lines = view.renderLines(n -> n.name());

            assertThat(lines).containsExactly("● X Node");
        }
    }

    @Nested
    @DisplayName("non-tree edge rendering")
    class NonTreeEdgeTest {

        @Test
        @DisplayName("ダイヤモンド DAG の非ツリーエッジを ┐ と ┘ で表現する")
        void shouldRenderNonTreeEdgesInDiamondDag() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            graph.addNode(nodeD);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text).contains("┐");
            assertThat(text).contains("┘");
        }
    }

    @Nested
    @DisplayName("reversed list constructor")
    class ReversedListConstructorTest {

        @Test
        @DisplayName("リスト外の依存を持つノードを reversed=true で渡しても例外にならず B を含む行を返す")
        void listConstructorReversed_singleNodeWithOutOfListDependency_doesNotThrow() {
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeB), true);

            List<String> lines = view.renderLines(n -> n.id().value() + ":" + n.name());

            assertThat(lines).anyMatch(line -> line.contains("b:Node B"));
        }
    }

    @Nested
    @DisplayName("topological order preservation (regression)")
    class TopologicalOrderRegressionTest {

        /**
         * 全ての依存 parent → child について「親の行 < 子の行」を満たすことをアサートする。
         *
         * <p>満たさないと、render 時に親が子より下に置かれた依存エッジが {@link
         * io.github.kakusuke.migraphe.core.graph.layout.GridCanvas#addNonTreeEdge} で「source >=
         * target」と判定され silently 破棄されるため、status の出力からその依存関係が消える。
         */
        private void assertAllDependencyEdgesPointDownward(
                ExecutionGraphView view, MigrationGraph graph) {
            List<NodeLineInfo> lines = view.lines();
            Map<NodeId, Integer> rowOf = new HashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                rowOf.put(lines.get(i).node().id(), i);
            }
            for (MigrationNode node : graph.allNodes()) {
                assertThat(rowOf)
                        .as("ノード %s が renderLines に含まれる", node.id())
                        .containsKey(node.id());
                int childRow = rowOf.get(node.id());
                for (NodeId parentId : graph.getDependencies(node.id())) {
                    assertThat(rowOf.get(parentId))
                            .as(
                                    "依存 %s → %s の親行は子行より小さくなければならない"
                                            + "（描画上で親が下にあると addNonTreeEdge が edge を破棄する）",
                                    parentId, node.id())
                            .isLessThan(childRow);
                }
            }
        }

        @Test
        @DisplayName(
                "A(root), B(root), C deps [A,B] で B→C エッジが render から消えない" + "（B は C より上に配置される）")
        void shouldKeepEdgeFromSecondRootDownwardToCommonChild() {
            // 現状の LayoutTree.build は order.nodes() 順に root を選び、最初の root A の
            // buildStream が trunk を A→C まで伸ばして C を bucket から奪う。続く root B は
            // 既に C を取れず孤立 stream として VR の最後の子として描画されるため、
            // B が C より下の行に置かれ、非ツリー辺 (B, C) は addNonTreeEdge で破棄される。
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);

            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            assertAllDependencyEdgesPointDownward(view, graph);
        }

        @Test
        @DisplayName(
                "A(root), B(root), C deps [A,B], D deps C, E deps A で全ての依存が render 上で下向き"
                        + "（sample/cli の currencies/locales → users 構造の縮約版）")
        void shouldKeepAllDependenciesDownwardWithFanInAndExtraBranch() {
            // sample/cli の mysql/01_common/001_currencies + 002_locales → pg/02_users/001_users
            // と同形の構造を最小化したもの:
            //   A,B が独立 root、C が両方に依存、D が C の唯一の子、E は A だけに依存。
            // LayoutTree.build は A の trunk を A→E まで伸ばし C を child stream として取り込むため、
            // B が C より下に置かれて B→C エッジが描画から消える。
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            MigrationNode nodeD = node("d").dependencies(NodeId.of("c")).build();
            MigrationNode nodeE = node("e").dependencies(NodeId.of("a")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            graph.addNode(nodeD);
            graph.addNode(nodeE);

            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            assertAllDependencyEdgesPointDownward(view, graph);
        }
    }

    @Nested
    @DisplayName("sample/cli DAG full render verification")
    class SampleCliRenderVerificationTest {

        /**
         * {@code sample/cli/tasks/**\/*.yaml} の全 19 ノードを忠実に再現した DAG を組んで、 render 結果を {@code
         * System.out} に出力し、全 dependencies が描画上で下向き (row(parent) &lt; row(child)) に
         * 配置されることを検証する。手動目視 + 自動アサート両方を兼ねる。
         */
        @Test
        @DisplayName("sample/cli の全 19 ノード DAG を render して、各 dependencies が grid 上で漏れなく下向きになる")
        void sampleCliFullDagRendersAllDependenciesDownward() {
            MigrationGraph graph = MigrationGraph.create();

            MigrationNode mCurrencies = node("mysql/01_common/001_currencies").build();
            MigrationNode mLocales = node("mysql/01_common/002_locales").build();
            MigrationNode mCategories = node("mysql/02_catalog/001_categories").build();
            MigrationNode mBrands = node("mysql/02_catalog/002_brands").build();
            MigrationNode mProducts =
                    node("mysql/02_catalog/003_products")
                            .dependencies(
                                    NodeId.of("mysql/02_catalog/001_categories"),
                                    NodeId.of("mysql/02_catalog/002_brands"))
                            .build();
            MigrationNode mVariants =
                    node("mysql/02_catalog/004_variants")
                            .dependencies(
                                    NodeId.of("mysql/02_catalog/003_products"),
                                    NodeId.of("mysql/01_common/001_currencies"))
                            .build();
            MigrationNode mImages =
                    node("mysql/02_catalog/005_images")
                            .dependencies(NodeId.of("mysql/02_catalog/003_products"))
                            .build();
            MigrationNode pUsers =
                    node("pg/02_users/001_users")
                            .dependencies(
                                    NodeId.of("mysql/01_common/001_currencies"),
                                    NodeId.of("mysql/01_common/002_locales"))
                            .build();
            MigrationNode mReviews =
                    node("mysql/03_reviews/001_reviews")
                            .dependencies(
                                    NodeId.of("mysql/02_catalog/003_products"),
                                    NodeId.of("pg/02_users/001_users"))
                            .build();
            MigrationNode mProductIndexes =
                    node("mysql/04_indexes/001_product_indexes")
                            .dependencies(
                                    NodeId.of("mysql/02_catalog/003_products"),
                                    NodeId.of("mysql/02_catalog/004_variants"))
                            .build();
            MigrationNode mReviewIndexes =
                    node("mysql/04_indexes/002_review_indexes")
                            .dependencies(NodeId.of("mysql/03_reviews/001_reviews"))
                            .build();
            MigrationNode pProfiles =
                    node("pg/02_users/002_profiles")
                            .dependencies(NodeId.of("pg/02_users/001_users"))
                            .build();
            MigrationNode pAddresses =
                    node("pg/02_users/003_addresses")
                            .dependencies(NodeId.of("pg/02_users/001_users"))
                            .build();
            MigrationNode pOrders =
                    node("pg/05_orders/001_orders")
                            .dependencies(
                                    NodeId.of("pg/02_users/001_users"),
                                    NodeId.of("pg/02_users/003_addresses"),
                                    NodeId.of("mysql/01_common/001_currencies"))
                            .build();
            MigrationNode pOrderItems =
                    node("pg/05_orders/002_order_items")
                            .dependencies(
                                    NodeId.of("pg/05_orders/001_orders"),
                                    NodeId.of("mysql/02_catalog/004_variants"))
                            .build();
            MigrationNode pPaymentMethods =
                    node("pg/06_payments/001_payment_methods")
                            .dependencies(NodeId.of("pg/02_users/001_users"))
                            .build();
            MigrationNode pPayments =
                    node("pg/06_payments/002_payments")
                            .dependencies(
                                    NodeId.of("pg/05_orders/001_orders"),
                                    NodeId.of("pg/06_payments/001_payment_methods"))
                            .build();
            MigrationNode pUserIndexes =
                    node("pg/07_indexes/001_user_indexes")
                            .dependencies(
                                    NodeId.of("pg/02_users/001_users"),
                                    NodeId.of("pg/02_users/002_profiles"))
                            .build();
            MigrationNode pOrderIndexes =
                    node("pg/07_indexes/002_order_indexes")
                            .dependencies(
                                    NodeId.of("pg/05_orders/001_orders"),
                                    NodeId.of("pg/05_orders/002_order_items"),
                                    NodeId.of("pg/06_payments/002_payments"))
                            .build();

            for (MigrationNode n :
                    List.of(
                            mCurrencies,
                            mLocales,
                            mCategories,
                            mBrands,
                            mProducts,
                            mVariants,
                            mImages,
                            pUsers,
                            mReviews,
                            mProductIndexes,
                            mReviewIndexes,
                            pProfiles,
                            pAddresses,
                            pOrders,
                            pOrderItems,
                            pPaymentMethods,
                            pPayments,
                            pUserIndexes,
                            pOrderIndexes)) {
                graph.addNode(n);
            }

            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            // 1. 出力を System.out にダンプ（テスト結果ログで確認できる）
            String rendered = view.toString();
            System.out.println();
            System.out.println("===== sample/cli rendered graph =====");
            System.out.println(rendered);
            System.out.println("=====================================");

            // 2. 全ノードがレンダリングされる
            List<NodeLineInfo> lines = view.lines();
            assertThat(lines).hasSize(19);

            // 3. 各 dependency が grid 上で下向き (row(parent) < row(child))
            Map<NodeId, Integer> rowOf = new HashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                rowOf.put(lines.get(i).node().id(), i);
            }
            for (MigrationNode node : graph.allNodes()) {
                int childRow = rowOf.get(node.id());
                for (NodeId parentId : graph.getDependencies(node.id())) {
                    assertThat(rowOf.get(parentId))
                            .as("依存 %s → %s が render 上で下向きに描画される", parentId, node.id())
                            .isLessThan(childRow);
                }
            }
        }
    }

    @Nested
    @DisplayName("lines()")
    class LinesTest {

        @Test
        @DisplayName("ソート済みノードを NodeLineInfo(column=0) で返す")
        void shouldReturnNodeLineInfosWithColumnZero() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<NodeLineInfo> lines = view.lines();

            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).node()).isEqualTo(nodeA);
            assertThat(lines.get(0).column()).isEqualTo(0);
            assertThat(lines.get(1).node()).isEqualTo(nodeB);
            assertThat(lines.get(1).column()).isEqualTo(0);
        }

        @Test
        @DisplayName("入力順序を保持する")
        void shouldPreserveInputOrder() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("b")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<NodeLineInfo> lines = view.lines();

            assertThat(lines).extracting(NodeLineInfo::node).containsExactly(nodeA, nodeB, nodeC);
        }
    }
}
