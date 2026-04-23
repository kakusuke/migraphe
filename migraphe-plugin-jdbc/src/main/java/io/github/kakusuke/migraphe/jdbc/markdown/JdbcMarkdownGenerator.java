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

/** JDBC スキーマ情報から Markdown ドキュメントを生成する。 */
public class JdbcMarkdownGenerator {

    private static final Pattern DEFAULT_SCHEMA_EXCLUDE =
            Pattern.compile("information_schema", Pattern.CASE_INSENSITIVE);

    private final String name;
    private final JdbcSchemaInfo schemaInfo;
    private final List<JdbcMarkdownDefinition.ExcludePattern> excludes;

    protected String name() {
        return name;
    }

    public JdbcMarkdownGenerator(
            String name,
            JdbcSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes) {
        this.name = name;
        this.schemaInfo = schemaInfo;
        this.excludes = excludes;
    }

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
                    if (isTableExcluded(table.name())) {
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
            if (exclude.schema().isPresent() && exclude.table().isPresent()) {
                Pattern pattern = Pattern.compile(exclude.schema().get(), Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(schemaName).matches()) {
                    // Schema+table pattern: schema match checked here, table checked separately
                    // This excludes the whole schema only if no table pattern
                }
            }
        }
        return false;
    }

    protected boolean isTableExcluded(String tableName) {
        for (JdbcMarkdownDefinition.ExcludePattern exclude : excludes) {
            if (exclude.table().isPresent()) {
                Pattern pattern = Pattern.compile(exclude.table().get(), Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(tableName).matches()) {
                    return true;
                }
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

    protected List<String> extraTableIndexHeaders() {
        return List.of();
    }

    protected List<String> extraTableIndexCells(String schemaName, JdbcTableInfo table) {
        return List.of();
    }

    protected List<String> extraViewIndexHeaders() {
        return List.of();
    }

    protected List<String> extraViewIndexCells(String schemaName, JdbcViewInfo view) {
        return List.of();
    }

    protected void appendIndexHeader(StringBuilder sb) {}

    protected void appendTableFileHeader(
            StringBuilder sb, String schemaName, JdbcTableInfo table) {}

    protected void appendViewFileHeader(StringBuilder sb, String schemaName, JdbcViewInfo view) {}

    protected void appendSchemaIndexSections(StringBuilder sb, String schemaName, Path outputDir) {}

    protected void appendTableSections(StringBuilder sb, String schemaName, String tableName) {}

    protected void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
