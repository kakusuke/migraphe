package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcColumnInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcForeignKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcIndexColumn;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcIndexInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Renders JDBC schema information into a set of Markdown documents.
 *
 * <p>Given a {@link JdbcSchemaInfo}, this generator produces an {@code index.md} listing every
 * (non-excluded) schema with its tables and views, plus one Markdown file per table and per view
 * describing columns, primary keys, foreign and exported keys, indexes, and view definitions.
 * Output is laid out beneath a per-database directory named after {@link #name()}.
 *
 * <p>The class is a Template Method base: dialect-specific generators (such as the PostgreSQL and
 * MySQL Markdown generators) subclass it and override the protected {@code append*} and {@code
 * extra*} hooks to inject extra index columns and document sections without re-implementing the
 * core traversal. Schemas and tables can be filtered out via the exclusion patterns supplied at
 * construction; {@code information_schema} is always excluded by default.
 */
public class JdbcMarkdownGenerator {

    private static final Pattern DEFAULT_SCHEMA_EXCLUDE =
            Pattern.compile("information_schema", Pattern.CASE_INSENSITIVE);

    private static final Pattern MERMAID_SANITIZE_PATTERN = Pattern.compile("[^A-Za-z0-9_]");

    private static final Pattern VALID_LAYOUT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * Default value for the telescoping constructor's {@code erDiagramPerTableMaxEntities}
     * parameter. Must be kept in sync with {@link
     * JdbcMarkdownDefinition#erDiagramPerTableMaxEntities()}'s {@code @WithDefault("60")} so that
     * config-driven and directly-constructed generators behave identically by default.
     */
    protected static final int DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES = 60;

    private final String name;
    private final JdbcSchemaInfo schemaInfo;
    private final List<CompiledExclude> excludes;
    private final boolean erDiagram;
    private final boolean erDiagramKeysOnly;
    private final String erDiagramLayout;
    private final boolean erDiagramPerTable;
    private final int erDiagramPerTableMaxEntities;

    /** Exact schema name -&gt; the first {@link JdbcSchemaDetail} with that name, built once. */
    private final Map<String, JdbcSchemaDetail> schemaByExactName;

    /**
     * Lower-cased (case-insensitive) schema name -&gt; matching {@link JdbcSchemaDetail}s, in
     * {@link JdbcSchemaInfo#schemas()} order, built once.
     */
    private final Map<String, List<JdbcSchemaDetail>> schemasByLowerName;

    /**
     * Table name -&gt; the {@link JdbcSchemaDetail}s that contain a table with that name, in {@link
     * JdbcSchemaInfo#schemas()} order, built once.
     */
    private final Map<String, List<JdbcSchemaDetail>> schemasByTableName;

    /**
     * Memoizes {@link #erEntityId(String, String)} results keyed by {@link #erIdKey(String,
     * String)}.
     */
    private final Map<String, String> erEntityIdCache = new HashMap<>();

    /**
     * Returns the database name used to title the output and to namespace the per-database output
     * directory.
     *
     * @return the database name supplied at construction
     */
    protected String name() {
        return name;
    }

    /**
     * Constructs a generator for the given database.
     *
     * @param name the database name used in titles, links, and the output directory layout
     * @param schemaInfo the schema information to render
     * @param excludes the schema/table exclusion patterns to apply (may be empty)
     */
    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes) {
        this(name, schemaInfo, excludes, true);
    }

    /**
     * Constructs a generator for the given database.
     *
     * @param name the database name used in titles, links, and the output directory layout
     * @param schemaInfo the schema information to render
     * @param excludes the schema/table exclusion patterns to apply (may be empty)
     * @param erDiagram whether the ER Diagram section is emitted in {@code index.md}
     */
    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram) {
        this(name, schemaInfo, excludes, erDiagram, false);
    }

    /**
     * Constructs a generator for the given database.
     *
     * @param name the database name used in titles, links, and the output directory layout
     * @param schemaInfo the schema information to render
     * @param excludes the schema/table exclusion patterns to apply (may be empty)
     * @param erDiagram whether the ER Diagram section is emitted in {@code index.md}
     * @param erDiagramKeysOnly whether the ER Diagram limits entity columns to primary-key and
     *     foreign-key columns
     */
    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly) {
        this(name, schemaInfo, excludes, erDiagram, erDiagramKeysOnly, "");
    }

    /**
     * Constructs a generator for the given database.
     *
     * @param name the database name used in titles, links, and the output directory layout
     * @param schemaInfo the schema information to render
     * @param excludes the schema/table exclusion patterns to apply (may be empty)
     * @param erDiagram whether the ER Diagram section is emitted in {@code index.md}
     * @param erDiagramKeysOnly whether the ER Diagram limits entity columns to primary-key and
     *     foreign-key columns
     * @param erDiagramLayout the Mermaid layout engine to configure via a frontmatter block, or
     *     null or empty to omit the frontmatter
     */
    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly,
            @Nullable String erDiagramLayout) {
        this(name, schemaInfo, excludes, erDiagram, erDiagramKeysOnly, erDiagramLayout, true);
    }

    /**
     * Constructs a generator for the given database.
     *
     * @param name the database name used in titles, links, and the output directory layout
     * @param schemaInfo the schema information to render
     * @param excludes the schema/table exclusion patterns to apply (may be empty)
     * @param erDiagram whether the ER Diagram section is emitted in {@code index.md}
     * @param erDiagramKeysOnly whether the ER Diagram limits entity columns to primary-key and
     *     foreign-key columns
     * @param erDiagramLayout the Mermaid layout engine to configure via a frontmatter block, or
     *     null or empty to omit the frontmatter
     * @param erDiagramPerTable whether a neighborhood ER Diagram section is emitted on each table's
     *     own Markdown document
     */
    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
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
     * Constructs a generator for the given database.
     *
     * @param name the database name used in titles, links, and the output directory layout
     * @param schemaInfo the schema information to render
     * @param excludes the schema/table exclusion patterns to apply (may be empty)
     * @param erDiagram whether the ER Diagram section is emitted in {@code index.md}
     * @param erDiagramKeysOnly whether the ER Diagram limits entity columns to primary-key and
     *     foreign-key columns
     * @param erDiagramLayout the Mermaid layout engine to configure via a frontmatter block, or
     *     null or empty to omit the frontmatter
     * @param erDiagramPerTable whether a neighborhood ER Diagram section is emitted on each table's
     *     own Markdown document
     * @param erDiagramPerTableMaxEntities the maximum number of entities a table's neighborhood may
     *     contain before its per-table ER Diagram is omitted in favor of a link to the full
     *     diagram; {@code 0} or less means unlimited
     */
    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes,
            boolean erDiagram,
            boolean erDiagramKeysOnly,
            @Nullable String erDiagramLayout,
            boolean erDiagramPerTable,
            int erDiagramPerTableMaxEntities) {
        this.name = name;
        this.schemaInfo = schemaInfo;
        this.excludes = excludes.stream().map(JdbcMarkdownGenerator::compileExclude).toList();
        this.erDiagram = erDiagram;
        this.erDiagramKeysOnly = erDiagramKeysOnly;
        this.erDiagramLayout = erDiagramLayout != null ? erDiagramLayout : "";
        this.erDiagramPerTable = erDiagramPerTable;
        this.erDiagramPerTableMaxEntities = erDiagramPerTableMaxEntities;

        this.schemaByExactName = new HashMap<>();
        this.schemasByLowerName = new HashMap<>();
        this.schemasByTableName = new HashMap<>();
        for (JdbcSchemaDetail schema : schemaInfo.schemas()) {
            schemaByExactName.putIfAbsent(schema.name(), schema);
            schemasByLowerName
                    .computeIfAbsent(schema.name().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(schema);
            for (JdbcTableInfo table : schema.tables()) {
                schemasByTableName
                        .computeIfAbsent(table.name(), k -> new ArrayList<>())
                        .add(schema);
            }
        }
    }

    /**
     * An {@link JdbcMarkdownDefinition.ExcludePattern} with its regular expressions precompiled
     * once at construction, so repeated calls to {@link #isSchemaExcluded(String)} and {@link
     * #isTableExcluded(String, String)} do not recompile the same pattern.
     */
    private record CompiledExclude(
            @Nullable Pattern schemaPattern, @Nullable Pattern tablePattern) {}

    private static CompiledExclude compileExclude(JdbcMarkdownDefinition.ExcludePattern exclude) {
        Pattern schemaPattern =
                exclude.schema()
                        .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                        .orElse(null);
        Pattern tablePattern =
                exclude.table().map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE)).orElse(null);
        return new CompiledExclude(schemaPattern, tablePattern);
    }

    /**
     * Generates the full set of Markdown documentation under the given directory.
     *
     * <p>Writes an {@code index.md} summarizing all non-excluded schemas (with their tables and
     * views) and emits one file per table and per view. Excluded schemas and tables are skipped,
     * and subclass hooks contribute any extra index columns and sections.
     *
     * @param outputDir the directory into which the Markdown files are written
     * @throws java.io.UncheckedIOException if writing any file fails
     */
    public void generate(Path outputDir) {
        var indexBuilder = new StringBuilder();
        indexBuilder.append("# Database: ").append(name).append("\n\n");
        appendIndexHeader(indexBuilder);
        appendErDiagram(indexBuilder);

        for (JdbcSchemaDetail schema : schemaInfo.schemas()) {
            if (isSchemaExcluded(schema.name())) {
                continue;
            }

            indexBuilder.append("## Schema: ").append(schema.name()).append("\n\n");

            if (!schema.tables().isEmpty()) {
                indexBuilder.append("### Tables\n\n");
                List<String> extraHeaders = extraTableIndexHeaders();
                appendIndexTableHeader(indexBuilder, extraHeaders);
                for (JdbcTableInfo table : schema.tables()) {
                    if (isTableExcluded(schema.name(), table.name())) {
                        continue;
                    }
                    generateTableFile(outputDir, schema.name(), table);
                    List<String> extraCells = extraTableIndexCells(schema.name(), table);
                    assertExtraSizeMatches(extraCells, extraHeaders, "extraTableIndexCells");
                    appendIndexRow(
                            indexBuilder,
                            table.name(),
                            schema.name(),
                            "tables",
                            extraCells,
                            table.remarks());
                }
                indexBuilder.append("\n");
            }

            if (!schema.views().isEmpty()) {
                indexBuilder.append("### Views\n\n");
                List<String> viewExtraHeaders = extraViewIndexHeaders();
                appendIndexTableHeader(indexBuilder, viewExtraHeaders);
                for (JdbcViewInfo view : schema.views()) {
                    generateViewFile(outputDir, schema.name(), view);
                    List<String> viewExtraCells = extraViewIndexCells(schema.name(), view);
                    assertExtraSizeMatches(viewExtraCells, viewExtraHeaders, "extraViewIndexCells");
                    appendIndexRow(
                            indexBuilder,
                            view.name(),
                            schema.name(),
                            "views",
                            viewExtraCells,
                            view.remarks());
                }
                indexBuilder.append("\n");
            }

            appendSchemaIndexSections(indexBuilder, schema.name(), outputDir);
        }

        writeFile(outputDir.resolve("index.md"), indexBuilder.toString());
    }

    private boolean isSchemaExcluded(String schemaName) {
        if (DEFAULT_SCHEMA_EXCLUDE.matcher(schemaName).matches()) {
            return true;
        }
        for (CompiledExclude exclude : excludes) {
            if (exclude.schemaPattern() != null && exclude.tablePattern() == null) {
                if (exclude.schemaPattern().matcher(schemaName).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reports whether a table should be omitted from the documentation.
     *
     * <p>A table is excluded when an {@link JdbcMarkdownDefinition.ExcludePattern} with a {@code
     * table} pattern matches the table name (case-insensitively) and either has no {@code schema}
     * pattern or has one that matches {@code schemaName}.
     *
     * @param schemaName the name of the schema containing the table
     * @param tableName the table name to test
     * @return {@code true} if the table matches an exclusion rule, otherwise {@code false}
     */
    protected boolean isTableExcluded(String schemaName, String tableName) {
        for (CompiledExclude exclude : excludes) {
            if (exclude.tablePattern() == null) {
                continue;
            }
            if (!exclude.tablePattern().matcher(tableName).matches()) {
                continue;
            }
            if (exclude.schemaPattern() == null) {
                return true;
            }
            if (exclude.schemaPattern().matcher(schemaName).matches()) {
                return true;
            }
        }
        return false;
    }

    private void generateTableFile(Path outputDir, String schemaName, JdbcTableInfo table) {
        var sb = new StringBuilder();
        sb.append("# ").append(table.name()).append("\n\n");
        appendRemarksParagraph(sb, table.remarks());
        appendTableFileHeader(sb, schemaName, table);
        appendTableErDiagram(sb, schemaName, table);

        // Columns section
        sb.append("## Columns\n\n");
        sb.append("| Name | Type | Nullable | Default | Auto Increment | Remarks |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        for (JdbcColumnInfo col : table.columns()) {
            sb.append("| ")
                    .append(col.name())
                    .append(" | ")
                    .append(formatType(col))
                    .append(" | ")
                    .append(col.nullable() ? "YES" : "NO")
                    .append(" | ")
                    .append(col.defaultValue() != null ? col.defaultValue() : "")
                    .append(" | ")
                    .append(col.autoIncrement() ? "YES" : "NO")
                    .append(" | ")
                    .append(col.remarks() != null ? col.remarks() : "")
                    .append(" |\n");
        }
        sb.append("\n");

        // Primary Key section
        if (table.primaryKey() != null && !table.primaryKey().columns().isEmpty()) {
            sb.append("## Primary Key\n\n");
            sb.append("**")
                    .append(table.primaryKey().name())
                    .append("**: ")
                    .append(String.join(", ", table.primaryKey().columns()))
                    .append("\n\n");
        }

        // Foreign Keys section
        if (!table.foreignKeys().isEmpty()) {
            sb.append("## Foreign Keys\n\n");
            sb.append("| Name | Columns | References | Update Rule | Delete Rule |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (JdbcForeignKeyInfo fk : table.foreignKeys()) {
                sb.append("| ")
                        .append(fk.name())
                        .append(" | ")
                        .append(String.join(", ", fk.columns()))
                        .append(" | ")
                        .append(referencedTableLink(schemaName, fk))
                        .append("(")
                        .append(String.join(", ", fk.referencedColumns()))
                        .append(")")
                        .append(" | ")
                        .append(fk.updateRule())
                        .append(" | ")
                        .append(fk.deleteRule())
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // Exported Keys section
        if (!table.exportedKeys().isEmpty()) {
            sb.append("## Exported Keys\n\n");
            sb.append("| Name | Columns | Referenced By | Update Rule | Delete Rule |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (JdbcForeignKeyInfo ek : table.exportedKeys()) {
                sb.append("| ")
                        .append(ek.name())
                        .append(" | ")
                        .append(String.join(", ", ek.columns()))
                        .append(" | ")
                        .append(referencedTableLink(schemaName, ek))
                        .append(" | ")
                        .append(ek.updateRule())
                        .append(" | ")
                        .append(ek.deleteRule())
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // Indexes section
        if (!table.indexes().isEmpty()) {
            sb.append("## Indexes\n\n");
            sb.append("| Name | Unique | Columns |\n");
            sb.append("| --- | --- | --- |\n");
            for (JdbcIndexInfo idx : table.indexes()) {
                sb.append("| ")
                        .append(idx.name())
                        .append(" | ")
                        .append(idx.unique() ? "YES" : "NO")
                        .append(" | ")
                        .append(
                                idx.columns().stream()
                                        .map(JdbcIndexColumn::name)
                                        .collect(Collectors.joining(", ")))
                        .append(" |\n");
            }
            sb.append("\n");
        }

        appendTableSections(sb, schemaName, table.name());

        Path filePath =
                outputDir
                        .resolve(name)
                        .resolve(schemaName)
                        .resolve("tables")
                        .resolve(table.name() + ".md");
        writeFile(filePath, sb.toString());
    }

    private void generateViewFile(Path outputDir, String schemaName, JdbcViewInfo view) {
        var sb = new StringBuilder();
        sb.append("# ").append(view.name()).append("\n\n");
        appendRemarksParagraph(sb, view.remarks());
        appendViewFileHeader(sb, schemaName, view);

        // Columns section
        sb.append("## Columns\n\n");
        sb.append("| Name | Type | Nullable | Default | Auto Increment | Remarks |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        for (JdbcColumnInfo col : view.columns()) {
            sb.append("| ")
                    .append(col.name())
                    .append(" | ")
                    .append(formatType(col))
                    .append(" | ")
                    .append(col.nullable() ? "YES" : "NO")
                    .append(" | ")
                    .append(col.defaultValue() != null ? col.defaultValue() : "")
                    .append(" | ")
                    .append(col.autoIncrement() ? "YES" : "NO")
                    .append(" | ")
                    .append(col.remarks() != null ? col.remarks() : "")
                    .append(" |\n");
        }
        sb.append("\n");

        // Definition section
        sb.append("## Definition\n\n");
        sb.append("```sql\n");
        sb.append(view.definition());
        sb.append("\n```\n");

        Path filePath =
                outputDir
                        .resolve(name)
                        .resolve(schemaName)
                        .resolve("views")
                        .resolve(view.name() + ".md");
        writeFile(filePath, sb.toString());
    }

    /**
     * Resolves the schema in which a foreign key's referenced table lives.
     *
     * <p>{@link JdbcForeignKeyInfo#referencedSchema()} is empty when the JDBC driver does not
     * report a cross-schema reference, in which case the referenced table is assumed to live in the
     * same schema as the table declaring the foreign key.
     *
     * @param schemaName the schema containing the table that declares the foreign key
     * @param fk the foreign key being rendered
     * @return {@code fk.referencedSchema()} if non-empty, otherwise {@code schemaName}
     */
    private String resolveReferencedSchema(String schemaName, JdbcForeignKeyInfo fk) {
        String refSchema = fk.referencedSchema();
        String refTable = fk.referencedTable();
        if (refSchema.isEmpty()) {
            if (schemaContainsTable(schemaName, refTable)) {
                return schemaName;
            }
            List<JdbcSchemaDetail> owning = schemasContainingTable(refTable);
            if (owning.size() == 1) {
                return owning.get(0).name();
            }
            return schemaName;
        }
        JdbcSchemaDetail exactMatch = schemaByExactName.get(refSchema);
        if (exactMatch != null) {
            return exactMatch.name();
        }
        List<JdbcSchemaDetail> caseInsensitiveMatches =
                schemasByLowerName.getOrDefault(refSchema.toLowerCase(Locale.ROOT), List.of());
        for (JdbcSchemaDetail schema : caseInsensitiveMatches) {
            if (schemaContainsTable(schema, refTable)) {
                return schema.name();
            }
        }
        if (!caseInsensitiveMatches.isEmpty()) {
            return caseInsensitiveMatches.get(0).name();
        }
        return refSchema;
    }

    private List<JdbcSchemaDetail> schemasContainingTable(String tableName) {
        return schemasByTableName.getOrDefault(tableName, List.of());
    }

    private boolean schemaContainsTable(String schemaName, String tableName) {
        JdbcSchemaDetail schema = schemaByExactName.get(schemaName);
        return schema != null && schemaContainsTable(schema, tableName);
    }

    private boolean schemaContainsTable(JdbcSchemaDetail schema, String tableName) {
        for (JdbcTableInfo table : schema.tables()) {
            if (table.name().equals(tableName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a Markdown link to a foreign or exported key's referenced table document.
     *
     * <p>Used identically by both the Foreign Keys and Exported Keys sections of a table's
     * document; the link target is {@code ../../<referencedSchema>/tables/<referencedTable>.md}.
     *
     * @param schemaName the schema containing the table that declares the key
     * @param fk the foreign or exported key being rendered
     * @return a Markdown link of the form {@code [referencedTable](../../schema/tables/table.md)}
     */
    private String referencedTableLink(String schemaName, JdbcForeignKeyInfo fk) {
        return "["
                + fk.referencedTable()
                + "](../../"
                + resolveReferencedSchema(schemaName, fk)
                + "/tables/"
                + fk.referencedTable()
                + ".md)";
    }

    private String formatType(JdbcColumnInfo col) {
        String typeName = cleanTypeName(col.typeName());
        if (isVarcharLike(col.dataType()) && col.size() > 0 && col.size() < Integer.MAX_VALUE) {
            return typeName + "(" + col.size() + ")";
        }
        return typeName;
    }

    private static String cleanTypeName(String rawTypeName) {
        String unquoted = rawTypeName.replace("\"", "");
        int lastDot = unquoted.lastIndexOf('.');
        if (lastDot >= 0) {
            String base = unquoted.substring(lastDot + 1);
            if (!base.isEmpty()) {
                return base;
            }
            String trimmed = unquoted;
            while (trimmed.endsWith(".")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return unquoted;
    }

    private boolean isVarcharLike(int dataType) {
        return dataType == Types.VARCHAR
                || dataType == Types.NVARCHAR
                || dataType == Types.CHAR
                || dataType == Types.NCHAR
                || dataType == Types.LONGVARCHAR
                || dataType == Types.LONGNVARCHAR;
    }

    private static void appendRemarksParagraph(StringBuilder sb, String remarks) {
        if (remarks != null && !remarks.isBlank()) {
            sb.append(remarks).append("\n\n");
        }
    }

    private static String formatIndexRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return " ";
        }
        return remarks.replace('\n', ' ').replace('\r', ' ').replace("|", "\\|");
    }

    private static void appendIndexTableHeader(StringBuilder sb, List<String> extraHeaders) {
        sb.append("| Name |");
        for (String h : extraHeaders) {
            sb.append(" ").append(h).append(" |");
        }
        sb.append(" Remarks |\n");
        sb.append("| ---");
        for (int i = 0; i < extraHeaders.size(); i++) {
            sb.append(" | ---");
        }
        sb.append(" | --- |\n");
    }

    private void appendIndexRow(
            StringBuilder sb,
            String entryName,
            String schemaName,
            String subDir,
            List<String> extraCells,
            String remarks) {
        sb.append("| [")
                .append(entryName)
                .append("](")
                .append(name)
                .append("/")
                .append(schemaName)
                .append("/")
                .append(subDir)
                .append("/")
                .append(entryName)
                .append(".md) |");
        for (String c : extraCells) {
            sb.append(" ").append(formatIndexRemarks(c)).append(" |");
        }
        sb.append(" ").append(formatIndexRemarks(remarks)).append(" |\n");
    }

    private static void assertExtraSizeMatches(
            List<String> cells, List<String> headers, String label) {
        if (cells.size() != headers.size()) {
            throw new IllegalStateException(
                    label
                            + " size ("
                            + cells.size()
                            + ") does not match "
                            + label.replace("Cells", "Headers")
                            + " size ("
                            + headers.size()
                            + ")");
        }
    }

    /**
     * Returns extra column headers to append to the table index table.
     *
     * <p>Subclasses override this to add dialect-specific columns; the returned headers must align
     * one-to-one with the cells from {@link #extraTableIndexCells(String, JdbcTableInfo)}. The base
     * implementation returns an empty list.
     *
     * @return the extra table-index header labels, empty by default
     */
    protected List<String> extraTableIndexHeaders() {
        return List.of();
    }

    /**
     * Returns extra index-row cells for a single table.
     *
     * <p>The returned cells must align one-to-one with {@link #extraTableIndexHeaders()}; a
     * mismatch raises an {@link IllegalStateException} during generation. The base implementation
     * returns an empty list.
     *
     * @param schemaName the schema containing the table
     * @param table the table whose extra index cells are requested
     * @return the extra cell values for this table's index row, empty by default
     */
    protected List<String> extraTableIndexCells(String schemaName, JdbcTableInfo table) {
        return List.of();
    }

    /**
     * Returns extra column headers to append to the view index table.
     *
     * <p>The returned headers must align one-to-one with {@link #extraViewIndexCells(String,
     * JdbcViewInfo)}. The base implementation returns an empty list.
     *
     * @return the extra view-index header labels, empty by default
     */
    protected List<String> extraViewIndexHeaders() {
        return List.of();
    }

    /**
     * Returns extra index-row cells for a single view.
     *
     * <p>The returned cells must align one-to-one with {@link #extraViewIndexHeaders()}; a mismatch
     * raises an {@link IllegalStateException} during generation. The base implementation returns an
     * empty list.
     *
     * @param schemaName the schema containing the view
     * @param view the view whose extra index cells are requested
     * @return the extra cell values for this view's index row, empty by default
     */
    protected List<String> extraViewIndexCells(String schemaName, JdbcViewInfo view) {
        return List.of();
    }

    /**
     * Appends extra content directly below the index document's top-level heading.
     *
     * <p>Invoked once while building {@code index.md}, before any schema sections. The base
     * implementation does nothing.
     *
     * @param sb the index document builder to append to
     */
    protected void appendIndexHeader(StringBuilder sb) {}

    /**
     * Appends the ER Diagram section to the index document, directly below the top-level heading.
     *
     * <p>Invoked once while building {@code index.md}, after {@link
     * #appendIndexHeader(StringBuilder)} and before any schema sections. Does nothing if {@code
     * erDiagram} was disabled at construction.
     *
     * @param sb the index document builder to append to
     */
    protected void appendErDiagram(StringBuilder sb) {
        if (!erDiagram) {
            return;
        }
        List<SchemaTable> tables = nonExcludedTables();
        if (tables.isEmpty()) {
            return;
        }
        appendErDiagramSection(sb, tables);
    }

    /**
     * Appends the ER Diagram section for a single table to its Markdown document, showing the table
     * itself plus every table transitively reachable by following foreign keys in either direction
     * (its ancestors and descendants).
     *
     * <p>Does nothing if {@code erDiagram} (the master switch) or {@code erDiagramPerTable} was
     * disabled at construction.
     *
     * @param sb the table document builder to append to
     * @param schemaName the schema containing the table
     * @param table the table being documented
     */
    protected void appendTableErDiagram(StringBuilder sb, String schemaName, JdbcTableInfo table) {
        if (!erDiagram || !erDiagramPerTable) {
            return;
        }
        List<SchemaTable> subset = neighborhoodOf(new TableRef(schemaName, table.name()));
        if (subset.isEmpty()) {
            return;
        }
        if (erDiagramPerTableMaxEntities > 0 && subset.size() > erDiagramPerTableMaxEntities) {
            appendErDiagramOmittedSection(sb, subset.size());
            return;
        }
        appendErDiagramSection(sb, subset);
    }

    /**
     * Appends an ER Diagram heading with a plain-text explanation in place of the Mermaid fence,
     * used when a table's neighborhood exceeds {@code erDiagramPerTableMaxEntities}.
     *
     * @param sb the table document builder to append to
     * @param entityCount the number of entities the omitted neighborhood would have contained
     */
    private void appendErDiagramOmittedSection(StringBuilder sb, int entityCount) {
        sb.append("## ER Diagram\n\n");
        sb.append("ER diagram omitted: this table's neighborhood includes ")
                .append(entityCount)
                .append(" entities, exceeding the configured limit of ")
                .append(erDiagramPerTableMaxEntities)
                .append(
                        ". See the full [ER diagram](../../../index.md) in the database index"
                                + " instead.\n\n");
    }

    /**
     * Collects {@code root} together with every table transitively reachable from it by following
     * foreign keys in either direction (its ancestors and descendants), in the same canonical order
     * as {@link #fkGraph()}'s {@code orderedTables}.
     *
     * @param root the table to center the neighborhood on
     * @return the neighborhood tables, in canonical order
     */
    private List<SchemaTable> neighborhoodOf(TableRef root) {
        Set<TableRef> members = new LinkedHashSet<>();
        collectReachable(root, fkGraph().forward(), members);
        collectReachable(root, fkGraph().backward(), members);
        return fkGraph().orderedTables().stream()
                .filter(st -> members.contains(refOf(st)))
                .toList();
    }

    /**
     * Adds {@code start} and every {@link TableRef} transitively reachable from it via {@code
     * adjacency} to {@code result}.
     *
     * <p>Uses its own, call-local {@code visited} set, so two calls sharing the same {@code result}
     * (e.g. one over the forward adjacency, one over the backward adjacency) traverse independently
     * without cross-contaminating each other's direction.
     *
     * @param start the table to start the traversal from; always included in {@code result}
     * @param adjacency the adjacency map to traverse (forward or backward FK edges)
     * @param result the set to add {@code start} and every reachable table to
     */
    private void collectReachable(
            TableRef start, Map<TableRef, Set<TableRef>> adjacency, Set<TableRef> result) {
        Set<TableRef> visited = new HashSet<>();
        visited.add(start);
        result.add(start);
        Deque<TableRef> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            TableRef cur = queue.remove();
            for (TableRef next : adjacency.getOrDefault(cur, Set.of())) {
                if (visited.add(next)) {
                    result.add(next);
                    queue.add(next);
                }
            }
        }
    }

    private record TableRef(String schemaName, String tableName) {}

    private static TableRef refOf(SchemaTable st) {
        return new TableRef(st.schemaName(), st.table().name());
    }

    private record FkGraph(
            List<SchemaTable> orderedTables,
            Map<TableRef, Set<TableRef>> forward,
            Map<TableRef, Set<TableRef>> backward) {}

    private @Nullable FkGraph fkGraph;

    private FkGraph fkGraph() {
        FkGraph g = fkGraph;
        if (g == null) {
            g = buildFkGraph();
            fkGraph = g;
        }
        return g;
    }

    private FkGraph buildFkGraph() {
        List<SchemaTable> ordered = nonExcludedTables();
        Set<TableRef> known =
                ordered.stream().map(JdbcMarkdownGenerator::refOf).collect(Collectors.toSet());
        Map<TableRef, Set<TableRef>> forward = new HashMap<>();
        Map<TableRef, Set<TableRef>> backward = new HashMap<>();
        for (SchemaTable st : ordered) {
            TableRef src = refOf(st);
            for (JdbcForeignKeyInfo fk : st.table().foreignKeys()) {
                String refSchema = resolveReferencedSchema(st.schemaName(), fk);
                TableRef dst = new TableRef(refSchema, fk.referencedTable());
                if (!known.contains(dst)) {
                    continue;
                }
                forward.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(dst);
                backward.computeIfAbsent(dst, k -> new LinkedHashSet<>()).add(src);
            }
        }
        return new FkGraph(ordered, forward, backward);
    }

    /**
     * Appends a Mermaid ER diagram section (heading, code fence, optional layout frontmatter,
     * entities, then relationships, then the closing fence) for the given tables.
     *
     * <p>Entities are rendered in one pass over {@code tables} before relationships are rendered in
     * a second, separate pass; a foreign key is only rendered as a relationship if its referenced
     * table is also present in {@code tables}. This two-pass shape must be preserved even though a
     * single-table caller could fuse both passes into one: for {@code index.md} (multiple tables),
     * fusing entity and relationship rendering into one pass would silently change the relative
     * order in which entities and relationships appear whenever a later table's entity is
     * referenced by an earlier table's foreign key.
     *
     * @param sb the document builder to append to
     * @param tables the tables to render as ER-diagram entities
     */
    private void appendErDiagramSection(StringBuilder sb, List<SchemaTable> tables) {
        sb.append("## ER Diagram\n\n```mermaid\n");
        if (VALID_LAYOUT_NAME_PATTERN.matcher(erDiagramLayout).matches()) {
            sb.append("---\nconfig:\n  layout: ").append(erDiagramLayout).append("\n---\n");
        }
        sb.append("erDiagram\n");
        Set<String> entityIds =
                tables.stream()
                        .map(st -> erEntityId(st.schemaName(), st.table().name()))
                        .collect(Collectors.toSet());
        for (SchemaTable st : tables) {
            appendErEntity(sb, st.schemaName(), st.table());
        }
        for (SchemaTable st : tables) {
            String entityId = erEntityId(st.schemaName(), st.table().name());
            for (JdbcForeignKeyInfo fk : st.table().foreignKeys()) {
                String refSchema = resolveReferencedSchema(st.schemaName(), fk);
                String refEntityId = erEntityId(refSchema, fk.referencedTable());
                if (entityIds.contains(refEntityId)) {
                    appendErRelationship(sb, refEntityId, entityId, fk);
                }
            }
        }
        sb.append("```\n\n");
    }

    private record SchemaTable(String schemaName, JdbcTableInfo table) {}

    private List<SchemaTable> nonExcludedTables() {
        List<SchemaTable> result = new ArrayList<>();
        for (JdbcSchemaDetail schema : schemaInfo.schemas()) {
            if (isSchemaExcluded(schema.name())) {
                continue;
            }
            for (JdbcTableInfo table : schema.tables()) {
                if (isTableExcluded(schema.name(), table.name())) {
                    continue;
                }
                result.add(new SchemaTable(schema.name(), table));
            }
        }
        return result;
    }

    /**
     * Builds the qualified Mermaid entity identifier for a table, combining its schema and table
     * name so that same-named tables in different schemas do not collide in the ER diagram.
     *
     * @param schemaName the schema containing the table
     * @param tableName the table name
     * @return the sanitized {@code <schema>_<table>_<hash>} entity identifier
     */
    private String erEntityId(String schemaName, String tableName) {
        return erEntityIdCache.computeIfAbsent(
                erIdKey(schemaName, tableName),
                k ->
                        sanitizeMermaid(schemaName)
                                + "_"
                                + sanitizeMermaid(tableName)
                                + "_"
                                + erIdHash(schemaName, tableName));
    }

    /**
     * Builds the injective {@code schemaName}/{@code tableName} combination shared by {@link
     * #erEntityId(String, String)}'s cache key and {@link #erIdHash(String, String)}'s hash input,
     * so both stay in sync and a change to one cannot silently break the other's uniqueness
     * guarantee.
     *
     * <p>The schema length is prefixed so that inputs are combined unambiguously (injectively),
     * rather than simply concatenating schema and table names.
     */
    private static String erIdKey(String schemaName, String tableName) {
        return schemaName.length() + ":" + schemaName + tableName;
    }

    /**
     * Computes a short hash suffix distinguishing entity identifiers whose sanitized {@code
     * <schema>_<table>} prefix would otherwise collide (e.g. {@code "a_b", "c"} vs. {@code "a",
     * "b_c"}).
     */
    private static String erIdHash(String schemaName, String tableName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest =
                    md.digest(erIdKey(schemaName, tableName).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void appendErEntity(StringBuilder sb, String schemaName, JdbcTableInfo table) {
        sb.append("  ")
                .append(erEntityId(schemaName, table.name()))
                .append("[\"")
                .append(sanitizeMermaidLabel(table.name()))
                .append("\"] {\n");
        for (JdbcColumnInfo col : table.columns()) {
            boolean pk = isPrimaryKeyColumn(table, col.name());
            boolean fk = isForeignKeyColumn(table, col.name());
            if (erDiagramKeysOnly && !pk && !fk) {
                continue;
            }
            sb.append("    ")
                    .append(sanitizeMermaid(cleanTypeName(col.typeName())))
                    .append(" ")
                    .append(sanitizeMermaid(col.name()));
            List<String> markers = new ArrayList<>();
            if (pk) {
                markers.add("PK");
            }
            if (fk) {
                markers.add("FK");
            }
            if (!markers.isEmpty()) {
                sb.append(" ").append(String.join(", ", markers));
            }
            sb.append("\n");
        }
        sb.append("  }\n");
    }

    private static boolean isPrimaryKeyColumn(JdbcTableInfo table, String columnName) {
        return table.primaryKey() != null && table.primaryKey().columns().contains(columnName);
    }

    private static boolean isForeignKeyColumn(JdbcTableInfo table, String columnName) {
        for (JdbcForeignKeyInfo fk : table.foreignKeys()) {
            if (fk.columns().contains(columnName)) {
                return true;
            }
        }
        return false;
    }

    private void appendErRelationship(
            StringBuilder sb, String refEntityId, String entityId, JdbcForeignKeyInfo fk) {
        String label = sanitizeMermaidLabel(fk.name());
        if (label.isEmpty()) {
            label = "fk";
        }
        sb.append("  ")
                .append(refEntityId)
                .append(" ||--o{ ")
                .append(entityId)
                .append(" : \"")
                .append(label)
                .append("\"\n");
    }

    /**
     * Normalizes an identifier or type name into a Mermaid-safe token.
     *
     * <p>Mermaid's {@code erDiagram} entity names and attribute type/name tokens must be bare
     * identifiers; whitespace, quotes, parentheses, dots and other punctuation (common in
     * schema-qualified or parameterized SQL type names such as {@code character varying(255)} or
     * {@code "app"."language_code"}) would break parsing. Every character outside {@code
     * [A-Za-z0-9_]} is replaced with an underscore. Applied consistently to both entity definitions
     * and relationship endpoints so the endpoints match the defined entities.
     *
     * @param token the raw identifier or type name
     * @return the sanitized token containing only letters, digits, and underscores
     */
    private static String sanitizeMermaid(String token) {
        return MERMAID_SANITIZE_PATTERN.matcher(token).replaceAll("_");
    }

    /**
     * Sanitizes a table or foreign-key name for use inside a Mermaid entity/relationship label
     * (which is wrapped in double quotes), removing characters that would otherwise break out of
     * the quoted string or the surrounding {@code ["..."]} alias syntax: double quotes, newlines
     * and carriage returns (collapsed to a single space), square brackets, and backslashes.
     *
     * @param tableName the raw table or foreign-key name
     * @return the name with quote-breaking and bracket/backslash characters removed
     */
    private static String sanitizeMermaidLabel(String tableName) {
        return tableName
                .replace("\"", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("[", "")
                .replace("]", "")
                .replace("\\", "");
    }

    /**
     * Appends extra content to a table's document, just below its title and remarks.
     *
     * <p>The base implementation does nothing.
     *
     * @param sb the table document builder to append to
     * @param schemaName the schema containing the table
     * @param table the table being documented
     */
    protected void appendTableFileHeader(
            StringBuilder sb, String schemaName, JdbcTableInfo table) {}

    /**
     * Appends extra content to a view's document, just below its title and remarks.
     *
     * <p>The base implementation does nothing.
     *
     * @param sb the view document builder to append to
     * @param schemaName the schema containing the view
     * @param view the view being documented
     */
    protected void appendViewFileHeader(StringBuilder sb, String schemaName, JdbcViewInfo view) {}

    /**
     * Appends extra index sections for a schema (for example sequences, functions, or other
     * dialect-specific objects) and may emit additional files.
     *
     * <p>Invoked once per non-excluded schema while building {@code index.md}, after its tables and
     * views have been listed. The base implementation does nothing.
     *
     * @param sb the index document builder to append to
     * @param schemaName the schema being documented
     * @param outputDir the root output directory, available for writing any additional files
     */
    protected void appendSchemaIndexSections(StringBuilder sb, String schemaName, Path outputDir) {}

    /**
     * Appends extra sections to a table's document, after the standard sections.
     *
     * <p>The base implementation does nothing.
     *
     * @param sb the table document builder to append to
     * @param schemaName the schema containing the table
     * @param tableName the name of the table being documented
     */
    protected void appendTableSections(StringBuilder sb, String schemaName, String tableName) {}

    /**
     * Writes a file's content, creating parent directories as needed.
     *
     * <p>Overridable so tests or alternative generators can intercept file output.
     *
     * @param path the destination file path
     * @param content the text content to write
     * @throws java.io.UncheckedIOException if the directories cannot be created or the write fails
     */
    protected void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Appends a {@code ## Definition} section rendering the given body as a fenced SQL block.
     *
     * <p>Nothing is appended when {@code definition} is {@code null} or blank, which is how an
     * unavailable body is represented (for example {@code
     * information_schema.ROUTINES.ROUTINE_DEFINITION} is {@code null} when the connected account
     * lacks the privilege to read it). The fence is grown one backtick past the longest backtick
     * run in the body, so a body that itself contains a Markdown fence cannot terminate the block
     * early.
     *
     * @param sb the buffer accumulating the detail page
     * @param definition the routine body to render, or {@code null} when it was not captured
     */
    protected static void appendDefinitionSection(StringBuilder sb, @Nullable String definition) {
        if (definition == null || definition.isBlank()) {
            return;
        }
        String fence = "`".repeat(Math.max(3, longestBacktickRun(definition) + 1));
        sb.append("\n## Definition\n\n")
                .append(fence)
                .append("sql\n")
                .append(definition)
                .append("\n")
                .append(fence)
                .append("\n");
    }

    private static int longestBacktickRun(String text) {
        int longest = 0;
        int current = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }
}
