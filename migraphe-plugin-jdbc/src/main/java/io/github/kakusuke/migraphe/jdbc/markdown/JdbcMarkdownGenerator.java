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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

    private final String name;
    private final JdbcSchemaInfo schemaInfo;
    private final List<CompiledExclude> excludes;
    private final boolean erDiagram;
    private final boolean erDiagramKeysOnly;

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
        this.name = name;
        this.schemaInfo = schemaInfo;
        this.excludes = excludes.stream().map(JdbcMarkdownGenerator::compileExclude).toList();
        this.erDiagram = erDiagram;
        this.erDiagramKeysOnly = erDiagramKeysOnly;
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
        return fk.referencedSchema().isEmpty()
                ? schemaName
                : normalizeSchemaName(fk.referencedSchema());
    }

    private String normalizeSchemaName(String schemaName) {
        for (JdbcSchemaDetail schema : schemaInfo.schemas()) {
            if (schema.name().equals(schemaName)) {
                return schema.name();
            }
        }
        for (JdbcSchemaDetail schema : schemaInfo.schemas()) {
            if (schema.name().equalsIgnoreCase(schemaName)) {
                return schema.name();
            }
        }
        return schemaName;
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
        return lastDot >= 0 ? unquoted.substring(lastDot + 1) : unquoted;
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
        sb.append("## ER Diagram\n\n```mermaid\nerDiagram\n");
        Set<String> entityIds =
                tables.stream()
                        .map(st -> erEntityId(st.schemaName(), st.table().name()))
                        .collect(Collectors.toSet());
        for (SchemaTable st : tables) {
            appendErEntity(sb, st.schemaName(), st.table());
        }
        for (SchemaTable st : tables) {
            for (JdbcForeignKeyInfo fk : st.table().foreignKeys()) {
                String refSchema = resolveReferencedSchema(st.schemaName(), fk);
                if (entityIds.contains(erEntityId(refSchema, fk.referencedTable()))) {
                    appendErRelationship(sb, st.schemaName(), st.table(), fk);
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
    private static String erEntityId(String schemaName, String tableName) {
        return sanitizeMermaid(schemaName)
                + "_"
                + sanitizeMermaid(tableName)
                + "_"
                + erIdHash(schemaName, tableName);
    }

    /**
     * Computes a short hash suffix distinguishing entity identifiers whose sanitized {@code
     * <schema>_<table>} prefix would otherwise collide (e.g. {@code "a_b", "c"} vs. {@code "a",
     * "b_c"}).
     *
     * <p>The schema length is prefixed to the hash input so that inputs are combined unambiguously
     * (injectively), rather than simply concatenating schema and table names.
     */
    private static String erIdHash(String schemaName, String tableName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String hashInput = schemaName.length() + ":" + schemaName + tableName;
            byte[] digest = md.digest(hashInput.getBytes(StandardCharsets.UTF_8));
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
            StringBuilder sb, String schemaName, JdbcTableInfo table, JdbcForeignKeyInfo fk) {
        String refSchema = resolveReferencedSchema(schemaName, fk);
        String refEntityId = erEntityId(refSchema, fk.referencedTable());
        String entityId = erEntityId(schemaName, table.name());
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
     * Minimally sanitizes a table name for use inside a Mermaid entity label (which is wrapped in
     * double quotes), removing any double quotes so the label cannot break out of its quoted
     * string. Ordinary table names are returned unchanged.
     *
     * @param tableName the raw table name
     * @return the table name with double quotes removed
     */
    private static String sanitizeMermaidLabel(String tableName) {
        return tableName.replace("\"", "").replace("\n", " ").replace("\r", " ");
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
}
