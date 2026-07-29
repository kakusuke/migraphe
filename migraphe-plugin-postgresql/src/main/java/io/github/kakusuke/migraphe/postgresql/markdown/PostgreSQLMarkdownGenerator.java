package io.github.kakusuke.migraphe.postgresql.markdown;

import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownGenerator;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSequenceInfo;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * PostgreSQL-specific Markdown generator.
 *
 * <p>Extends the generic {@link JdbcMarkdownGenerator} and overrides its template-method hooks to
 * emit PostgreSQL extras on top of the base table/view/column documentation: an {@code Owner}
 * column on table and view indexes, an {@code Owner} line in per-object files, top-level Extensions
 * and Enum Types tables, per-table Triggers/Policies/Partition sections, and per-schema
 * Sequences/Functions/Materialized Views/Triggers/Partitions/Policies sections (some of which also
 * write detail files). The base class drives the overall layout and file writing; this subclass
 * only fills in the PostgreSQL-specific portions read from the supplied {@link
 * PostgreSQLSchemaInfo}.
 */
public class PostgreSQLMarkdownGenerator extends JdbcMarkdownGenerator {

    private final PostgreSQLSchemaInfo pgInfo;

    /**
     * Creates a PostgreSQL Markdown generator.
     *
     * @param name the generator name, used as the root subdirectory for generated files
     * @param schemaInfo the PostgreSQL schema information to render
     * @param excludes patterns identifying tables/objects to omit from the output
     */
    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes) {
        this(name, schemaInfo, excludes, true);
    }

    /**
     * Creates a PostgreSQL Markdown generator.
     *
     * @param name the generator name, used as the root subdirectory for generated files
     * @param schemaInfo the PostgreSQL schema information to render
     * @param excludes patterns identifying tables/objects to omit from the output
     * @param erDiagram whether to emit the ER diagram section in {@code index.md}
     */
    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram) {
        this(name, schemaInfo, excludes, erDiagram, false);
    }

    /**
     * Creates a PostgreSQL Markdown generator.
     *
     * @param name the generator name, used as the root subdirectory for generated files
     * @param schemaInfo the PostgreSQL schema information to render
     * @param excludes patterns identifying tables/objects to omit from the output
     * @param erDiagram whether to emit the ER diagram section in {@code index.md}
     * @param erDiagramKeysOnly whether the ER diagram limits entity columns to PK/FK columns
     */
    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly) {
        this(name, schemaInfo, excludes, erDiagram, erDiagramKeysOnly, "");
    }

    /**
     * Creates a PostgreSQL Markdown generator.
     *
     * @param name the generator name, used as the root subdirectory for generated files
     * @param schemaInfo the PostgreSQL schema information to render
     * @param excludes patterns identifying tables/objects to omit from the output
     * @param erDiagram whether to emit the ER diagram section in {@code index.md}
     * @param erDiagramKeysOnly whether the ER diagram limits entity columns to PK/FK columns
     * @param erDiagramLayout the Mermaid layout engine to configure via a frontmatter block, or
     *     null or empty to omit the frontmatter
     */
    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly,
            @Nullable String erDiagramLayout) {
        this(name, schemaInfo, excludes, erDiagram, erDiagramKeysOnly, erDiagramLayout, true);
    }

    /**
     * Creates a PostgreSQL Markdown generator.
     *
     * @param name the generator name, used as the root subdirectory for generated files
     * @param schemaInfo the PostgreSQL schema information to render
     * @param excludes patterns identifying tables/objects to omit from the output
     * @param erDiagram whether to emit the ER diagram section in {@code index.md}
     * @param erDiagramKeysOnly whether the ER diagram limits entity columns to PK/FK columns
     * @param erDiagramLayout the Mermaid layout engine to configure via a frontmatter block, or
     *     null or empty to omit the frontmatter
     * @param erDiagramPerTable whether a neighborhood ER Diagram section is emitted on each table's
     *     own Markdown document
     */
    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly,
            @Nullable String erDiagramLayout,
            boolean erDiagramPerTable) {
        this(
                name,
                schemaInfo,
                excludes,
                erDiagram,
                erDiagramKeysOnly,
                erDiagramLayout,
                erDiagramPerTable,
                DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES);
    }

    /**
     * Creates a PostgreSQL Markdown generator.
     *
     * @param name the generator name, used as the root subdirectory for generated files
     * @param schemaInfo the PostgreSQL schema information to render
     * @param excludes patterns identifying tables/objects to omit from the output
     * @param erDiagram whether to emit the ER diagram section in {@code index.md}
     * @param erDiagramKeysOnly whether the ER diagram limits entity columns to PK/FK columns
     * @param erDiagramLayout the Mermaid layout engine to configure via a frontmatter block, or
     *     null or empty to omit the frontmatter
     * @param erDiagramPerTable whether a neighborhood ER Diagram section is emitted on each table's
     *     own Markdown document
     * @param erDiagramPerTableMaxEntities the maximum number of entities a table's neighborhood may
     *     contain before its per-table ER Diagram is omitted in favor of a link to the full
     *     diagram; {@code 0} or less means unlimited
     */
    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly,
            @Nullable String erDiagramLayout,
            boolean erDiagramPerTable,
            int erDiagramPerTableMaxEntities) {
        super(
                name,
                schemaInfo,
                excludes,
                erDiagram,
                erDiagramKeysOnly,
                erDiagramLayout,
                erDiagramPerTable,
                erDiagramPerTableMaxEntities);
        this.pgInfo = schemaInfo;
    }

    /**
     * Adds an {@code Owner} header to the table index when owner information is available.
     *
     * @return a single-element {@code ["Owner"]} list, or an empty list if no table owners are
     *     known
     */
    @Override
    protected List<String> extraTableIndexHeaders() {
        return pgInfo.tableOwners().isEmpty() ? List.of() : List.of("Owner");
    }

    /**
     * Supplies the {@code Owner} cell value for a table row in the index.
     *
     * @param schemaName the schema the table belongs to
     * @param table the table whose owner cell is being produced
     * @return a single-element list with the owner name (empty string if unknown), or an empty list
     *     when no table owners are known
     */
    @Override
    protected List<String> extraTableIndexCells(String schemaName, JdbcTableInfo table) {
        if (pgInfo.tableOwners().isEmpty()) {
            return List.of();
        }
        String owner = pgInfo.tableOwners().getOrDefault(schemaName + "." + table.name(), "");
        return List.of(owner);
    }

    /**
     * Adds an {@code Owner} header to the view index when owner information is available.
     *
     * @return a single-element {@code ["Owner"]} list, or an empty list if no view owners are known
     */
    @Override
    protected List<String> extraViewIndexHeaders() {
        return pgInfo.viewOwners().isEmpty() ? List.of() : List.of("Owner");
    }

    /**
     * Supplies the {@code Owner} cell value for a view row in the index.
     *
     * @param schemaName the schema the view belongs to
     * @param view the view whose owner cell is being produced
     * @return a single-element list with the owner name (empty string if unknown), or an empty list
     *     when no view owners are known
     */
    @Override
    protected List<String> extraViewIndexCells(String schemaName, JdbcViewInfo view) {
        if (pgInfo.viewOwners().isEmpty()) {
            return List.of();
        }
        String owner = pgInfo.viewOwners().getOrDefault(schemaName + "." + view.name(), "");
        return List.of(owner);
    }

    /**
     * Appends an {@code Owner:} line to a per-table Markdown file header.
     *
     * @param sb the buffer accumulating the table file content
     * @param schemaName the schema the table belongs to
     * @param table the table being documented
     */
    @Override
    protected void appendTableFileHeader(StringBuilder sb, String schemaName, JdbcTableInfo table) {
        String owner = pgInfo.tableOwners().get(schemaName + "." + table.name());
        if (owner != null && !owner.isBlank()) {
            sb.append("Owner: ").append(owner).append("\n\n");
        }
    }

    /**
     * Appends an {@code Owner:} line to a per-view Markdown file header.
     *
     * @param sb the buffer accumulating the view file content
     * @param schemaName the schema the view belongs to
     * @param view the view being documented
     */
    @Override
    protected void appendViewFileHeader(StringBuilder sb, String schemaName, JdbcViewInfo view) {
        String owner = pgInfo.viewOwners().get(schemaName + "." + view.name());
        if (owner != null && !owner.isBlank()) {
            sb.append("Owner: ").append(owner).append("\n\n");
        }
    }

    /**
     * Appends top-level {@code Extensions} and {@code Enum Types} tables to the main index file.
     *
     * <p>Each table is emitted only when the corresponding {@link PostgreSQLSchemaInfo} list is
     * non-empty.
     *
     * @param sb the buffer accumulating the main index content
     */
    @Override
    protected void appendIndexHeader(StringBuilder sb) {
        if (!pgInfo.extensions().isEmpty()) {
            sb.append("## Extensions\n\n");
            sb.append("| Name | Version | Owner |\n");
            sb.append("| --- | --- | --- |\n");
            for (var ext : pgInfo.extensions()) {
                sb.append("| ")
                        .append(ext.name())
                        .append(" | ")
                        .append(ext.version())
                        .append(" | ")
                        .append(ext.owner() != null ? ext.owner() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }
        if (!pgInfo.enums().isEmpty()) {
            sb.append("## Enum Types\n\n");
            sb.append("| Name | Labels | Owner |\n");
            sb.append("| --- | --- | --- |\n");
            for (var e : pgInfo.enums()) {
                sb.append("| ")
                        .append(e.name())
                        .append(" | ")
                        .append(String.join(", ", e.labels()))
                        .append(" | ")
                        .append(e.owner() != null ? e.owner() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    /**
     * Appends PostgreSQL-specific sections (Triggers, Policies, Partition Info) to a per-table
     * file.
     *
     * <p>Each section is filtered to the given schema and table and emitted only when non-empty.
     *
     * @param sb the buffer accumulating the table file content
     * @param schemaName the schema the table belongs to
     * @param tableName the name of the table being documented
     */
    @Override
    protected void appendTableSections(StringBuilder sb, String schemaName, String tableName) {
        var tableTriggers =
                pgInfo.triggers().stream()
                        .filter(
                                t ->
                                        t.schema().equals(schemaName)
                                                && t.tableName().equals(tableName))
                        .toList();
        if (!tableTriggers.isEmpty()) {
            sb.append("## Triggers\n\n");
            sb.append("| Name | Timing | Events | Function |\n");
            sb.append("| --- | --- | --- | --- |\n");
            for (var trig : tableTriggers) {
                sb.append("| ")
                        .append(trig.name())
                        .append(" | ")
                        .append(trig.timing())
                        .append(" | ")
                        .append(String.join(", ", trig.events()))
                        .append(" | ")
                        .append(trig.functionName())
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var tablePolicies =
                pgInfo.policies().stream()
                        .filter(
                                p ->
                                        p.schema().equals(schemaName)
                                                && p.tableName().equals(tableName))
                        .toList();
        if (!tablePolicies.isEmpty()) {
            sb.append("## Policies\n\n");
            sb.append("| Name | Command | USING | WITH CHECK |\n");
            sb.append("| --- | --- | --- | --- |\n");
            for (var pol : tablePolicies) {
                sb.append("| ")
                        .append(pol.name())
                        .append(" | ")
                        .append(pol.command())
                        .append(" | ")
                        .append(pol.usingExpression() != null ? pol.usingExpression() : "")
                        .append(" | ")
                        .append(pol.withCheckExpression() != null ? pol.withCheckExpression() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var tablePartitions =
                pgInfo.partitions().stream()
                        .filter(p -> p.schema().equals(schemaName) && p.name().equals(tableName))
                        .toList();
        if (!tablePartitions.isEmpty()) {
            sb.append("## Partition Info\n\n");
            sb.append("| Strategy | Partition Key |\n");
            sb.append("| --- | --- |\n");
            for (var part : tablePartitions) {
                sb.append("| ")
                        .append(part.strategy())
                        .append(" | ")
                        .append(part.partitionKey())
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    /**
     * Appends PostgreSQL-specific sections to a per-schema index file.
     *
     * <p>Emits, when non-empty, Sequences, Functions, Materialized Views, Triggers, Partitions, and
     * Policies sections for the given schema. The Functions and Materialized Views sections also
     * write individual detail files under {@code <name>/<schema>/functions/} and {@code
     * <name>/<schema>/materialized-views/} respectively, via {@link #writeFile(Path, String)}.
     *
     * @param sb the buffer accumulating the schema index content
     * @param schemaName the schema being documented
     * @param outputDir the root output directory under which detail files are written
     */
    @Override
    protected void appendSchemaIndexSections(StringBuilder sb, String schemaName, Path outputDir) {
        var sequences =
                pgInfo.sequences().stream().filter(s -> s.schema().equals(schemaName)).toList();
        if (!sequences.isEmpty()) {
            sb.append("### Sequences\n\n");
            sb.append(
                    "| Name | Type | Start | Increment | Min | Max | Cycle | Owned By | Owner |\n");
            sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (var seq : sequences) {
                String ownedBy = formatOwnedBy(schemaName, seq);
                sb.append("| ")
                        .append(seq.name())
                        .append(" | ")
                        .append(seq.dataType())
                        .append(" | ")
                        .append(seq.startValue())
                        .append(" | ")
                        .append(seq.increment())
                        .append(" | ")
                        .append(seq.minValue())
                        .append(" | ")
                        .append(seq.maxValue())
                        .append(" | ")
                        .append(seq.cycle())
                        .append(" | ")
                        .append(ownedBy)
                        .append(" | ")
                        .append(seq.owner() != null ? seq.owner() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var functions =
                pgInfo.functions().stream().filter(f -> f.schema().equals(schemaName)).toList();
        if (!functions.isEmpty()) {
            sb.append("### Functions\n\n");
            for (var func : functions) {
                String sanitized =
                        (func.name() + "_" + func.arguments())
                                .replaceAll("[^a-zA-Z0-9_]", "_")
                                .replaceAll("_+", "_");
                sb.append("- [")
                        .append(func.name())
                        .append("(")
                        .append(func.arguments())
                        .append(")](")
                        .append(name())
                        .append("/")
                        .append(schemaName)
                        .append("/functions/")
                        .append(sanitized)
                        .append(".md)\n");
                var fileSb = new StringBuilder();
                fileSb.append("# ").append(func.name()).append("\n\n");
                fileSb.append("| Property | Value |\n");
                fileSb.append("| --- | --- |\n");
                fileSb.append("| Schema | ").append(func.schema()).append(" |\n");
                fileSb.append("| Arguments | ").append(func.arguments()).append(" |\n");
                fileSb.append("| Return Type | ").append(func.returnType()).append(" |\n");
                fileSb.append("| Language | ").append(func.language()).append(" |\n");
                fileSb.append("| Type | ")
                        .append(func.isProcedure() ? "Procedure" : "Function")
                        .append(" |\n");
                if (func.owner() != null && !func.owner().isBlank()) {
                    fileSb.append("| Owner | ").append(func.owner()).append(" |\n");
                }
                Path funcFile =
                        outputDir
                                .resolve(name())
                                .resolve(schemaName)
                                .resolve("functions")
                                .resolve(sanitized + ".md");
                writeFile(funcFile, fileSb.toString());
            }
            sb.append("\n");
        }

        var matViews =
                pgInfo.materializedViews().stream()
                        .filter(m -> m.schema().equals(schemaName))
                        .toList();
        if (!matViews.isEmpty()) {
            sb.append("### Materialized Views\n\n");
            for (var mv : matViews) {
                sb.append("- [")
                        .append(mv.name())
                        .append("](")
                        .append(name())
                        .append("/")
                        .append(schemaName)
                        .append("/materialized-views/")
                        .append(mv.name())
                        .append(".md)\n");
                var fileSb = new StringBuilder();
                fileSb.append("# ").append(mv.name()).append("\n\n");
                fileSb.append("| Property | Value |\n");
                fileSb.append("| --- | --- |\n");
                fileSb.append("| Schema | ").append(mv.schema()).append(" |\n");
                fileSb.append("| Tablespace | ")
                        .append(mv.tablespace() != null ? mv.tablespace() : "default")
                        .append(" |\n");
                if (mv.owner() != null && !mv.owner().isBlank()) {
                    fileSb.append("| Owner | ").append(mv.owner()).append(" |\n");
                }
                fileSb.append("\n## Definition\n\n```sql\n")
                        .append(mv.definition())
                        .append("\n```\n");
                Path mvFile =
                        outputDir
                                .resolve(name())
                                .resolve(schemaName)
                                .resolve("materialized-views")
                                .resolve(mv.name() + ".md");
                writeFile(mvFile, fileSb.toString());
            }
            sb.append("\n");
        }

        var triggers =
                pgInfo.triggers().stream().filter(t -> t.schema().equals(schemaName)).toList();
        if (!triggers.isEmpty()) {
            sb.append("### Triggers\n\n");
            sb.append("| Name | Table | Timing | Events | Function | Constraint |\n");
            sb.append("| --- | --- | --- | --- | --- | --- |\n");
            for (var trig : triggers) {
                sb.append("| ")
                        .append(trig.name())
                        .append(" | ")
                        .append(trig.tableName())
                        .append(" | ")
                        .append(trig.timing())
                        .append(" | ")
                        .append(String.join(", ", trig.events()))
                        .append(" | ")
                        .append(trig.functionName())
                        .append(" | ")
                        .append(trig.isConstraint())
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var partitions =
                pgInfo.partitions().stream().filter(p -> p.schema().equals(schemaName)).toList();
        if (!partitions.isEmpty()) {
            sb.append("### Partitions\n\n");
            sb.append("| Name | Strategy | Partition Key |\n");
            sb.append("| --- | --- | --- |\n");
            for (var part : partitions) {
                sb.append("| ")
                        .append(part.name())
                        .append(" | ")
                        .append(part.strategy())
                        .append(" | ")
                        .append(part.partitionKey())
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var policies =
                pgInfo.policies().stream().filter(p -> p.schema().equals(schemaName)).toList();
        if (!policies.isEmpty()) {
            sb.append("### Policies\n\n");
            sb.append("| Name | Table | Command | USING | WITH CHECK |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (var pol : policies) {
                sb.append("| ")
                        .append(pol.name())
                        .append(" | ")
                        .append(pol.tableName())
                        .append(" | ")
                        .append(pol.command())
                        .append(" | ")
                        .append(pol.usingExpression() != null ? pol.usingExpression() : "")
                        .append(" | ")
                        .append(pol.withCheckExpression() != null ? pol.withCheckExpression() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    private String formatOwnedBy(String schemaName, PostgreSQLSequenceInfo seq) {
        if (seq.ownerTable() == null || seq.ownerColumn() == null) {
            return "";
        }
        boolean tableExists =
                pgInfo.schemas().stream()
                                .filter(sd -> sd.name().equals(schemaName))
                                .flatMap(sd -> sd.tables().stream())
                                .anyMatch(t -> t.name().equals(seq.ownerTable()))
                        && !isTableExcluded(schemaName, seq.ownerTable());
        return tableExists
                ? "["
                        + seq.ownerTable()
                        + "."
                        + seq.ownerColumn()
                        + "]("
                        + name()
                        + "/"
                        + schemaName
                        + "/tables/"
                        + seq.ownerTable()
                        + ".md)"
                : seq.ownerTable() + "." + seq.ownerColumn();
    }
}
