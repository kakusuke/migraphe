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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private final String name;
    private final JdbcSchemaInfo schemaInfo;
    private final List<JdbcMarkdownDefinition.ExcludePattern> excludes;

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
        this.name = name;
        this.schemaInfo = schemaInfo;
        this.excludes = excludes;
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
        for (JdbcMarkdownDefinition.ExcludePattern exclude : excludes) {
            if (exclude.schema().isPresent() && exclude.table().isEmpty()) {
                Pattern pattern = Pattern.compile(exclude.schema().get(), Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(schemaName).matches()) {
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
        for (JdbcMarkdownDefinition.ExcludePattern exclude : excludes) {
            if (exclude.table().isEmpty()) {
                continue;
            }
            Pattern tablePattern = Pattern.compile(exclude.table().get(), Pattern.CASE_INSENSITIVE);
            if (!tablePattern.matcher(tableName).matches()) {
                continue;
            }
            if (exclude.schema().isEmpty()) {
                return true;
            }
            Pattern schemaPattern =
                    Pattern.compile(exclude.schema().get(), Pattern.CASE_INSENSITIVE);
            if (schemaPattern.matcher(schemaName).matches()) {
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
                        .append("[")
                        .append(fk.referencedTable())
                        .append("](../tables/")
                        .append(fk.referencedTable())
                        .append(".md)")
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
                        .append("[")
                        .append(ek.referencedTable())
                        .append("](../tables/")
                        .append(ek.referencedTable())
                        .append(".md)")
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

    private String formatType(JdbcColumnInfo col) {
        if (isVarcharLike(col.dataType()) && col.size() > 0) {
            return col.typeName() + "(" + col.size() + ")";
        }
        return col.typeName();
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
