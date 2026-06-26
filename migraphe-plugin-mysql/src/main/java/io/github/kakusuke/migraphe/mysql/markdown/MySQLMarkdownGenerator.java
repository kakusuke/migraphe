package io.github.kakusuke.migraphe.mysql.markdown;

import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownGenerator;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import java.nio.file.Path;
import java.util.List;

/**
 * Markdown generator specialized for MySQL schema information.
 *
 * <p>Extends {@link JdbcMarkdownGenerator} and overrides its Template Method hooks to enrich the
 * generated documentation with MySQL-specific details: a server-wide storage-engine table on the
 * index page, a view {@code Definer} column, per-table properties/triggers/partitions, and
 * per-schema routines, triggers, events, and partitions. The base class drives the overall document
 * layout and calls these hooks at the appropriate points.
 *
 * @see JdbcMarkdownGenerator
 * @see MySQLSchemaInfo
 */
public class MySQLMarkdownGenerator extends JdbcMarkdownGenerator {

    private final MySQLSchemaInfo mysqlInfo;

    /**
     * Creates a MySQL Markdown generator.
     *
     * @param name the database name used as the documentation title and as the root directory for
     *     generated files
     * @param schemaInfo the MySQL schema information to render
     * @param excludes schema/table exclusion patterns applied during generation
     */
    public MySQLMarkdownGenerator(
            String name,
            MySQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes) {
        super(name, schemaInfo, excludes);
        this.mysqlInfo = schemaInfo;
    }

    /**
     * Adds a {@code Definer} column to the view index when any view has a recorded definer.
     *
     * @return a single-element list {@code ["Definer"]} when view definers are present, otherwise
     *     an empty list
     */
    @Override
    protected List<String> extraViewIndexHeaders() {
        return mysqlInfo.viewDefiners().isEmpty() ? List.of() : List.of("Definer");
    }

    /**
     * Supplies the {@code Definer} cell for a view row in the view index.
     *
     * @param schemaName the name of the schema the view belongs to
     * @param view the view being rendered
     * @return a single-element list with the view's definer (empty string if unknown), or an empty
     *     list when no view has a definer (matching {@link #extraViewIndexHeaders()})
     */
    @Override
    protected List<String> extraViewIndexCells(String schemaName, JdbcViewInfo view) {
        if (mysqlInfo.viewDefiners().isEmpty()) {
            return List.of();
        }
        String definer = mysqlInfo.viewDefiners().getOrDefault(schemaName + "." + view.name(), "");
        return List.of(definer);
    }

    /**
     * Appends a {@code Definer:} line to an individual view's documentation page when one is known.
     *
     * @param sb the buffer accumulating the view page Markdown
     * @param schemaName the name of the schema the view belongs to
     * @param view the view being rendered
     */
    @Override
    protected void appendViewFileHeader(StringBuilder sb, String schemaName, JdbcViewInfo view) {
        String definer = mysqlInfo.viewDefiners().get(schemaName + "." + view.name());
        if (definer != null && !definer.isBlank()) {
            sb.append("Definer: ").append(definer).append("\n\n");
        }
    }

    /**
     * Appends a server-wide "Storage Engines" table to the documentation index page.
     *
     * <p>The table lists each available storage engine with its support level and transaction, XA,
     * and savepoint capabilities. Nothing is appended when no storage-engine information is
     * available.
     *
     * @param sb the buffer accumulating the index page Markdown
     */
    @Override
    protected void appendIndexHeader(StringBuilder sb) {
        if (!mysqlInfo.storageEngines().isEmpty()) {
            sb.append("## Storage Engines\n\n");
            sb.append("| Name | Support | Transactions | XA | Savepoints |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (var engine : mysqlInfo.storageEngines()) {
                sb.append("| ")
                        .append(engine.name())
                        .append(" | ")
                        .append(engine.support())
                        .append(" | ")
                        .append(engine.transactions())
                        .append(" | ")
                        .append(engine.xa())
                        .append(" | ")
                        .append(engine.savepoints())
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    /**
     * Appends MySQL-specific sections to an individual table's documentation page.
     *
     * <p>For the named table this emits, when present, a "Table Properties" table (engine,
     * collation, row format, and optional comment), a "Triggers" table, and a "Partition Info"
     * table.
     *
     * @param sb the buffer accumulating the table page Markdown
     * @param schemaName the name of the schema the table belongs to
     * @param tableName the name of the table being rendered
     */
    @Override
    protected void appendTableSections(StringBuilder sb, String schemaName, String tableName) {
        var meta =
                mysqlInfo.tableMeta().stream()
                        .filter(
                                m ->
                                        m.schema().equals(schemaName)
                                                && m.tableName().equals(tableName))
                        .findFirst();
        if (meta.isPresent()) {
            var m = meta.get();
            sb.append("## Table Properties\n\n");
            sb.append("| Property | Value |\n");
            sb.append("| --- | --- |\n");
            sb.append("| Engine | ").append(m.engine()).append(" |\n");
            sb.append("| Collation | ").append(m.collation()).append(" |\n");
            sb.append("| Row Format | ").append(m.rowFormat()).append(" |\n");
            if (m.tableComment() != null && !m.tableComment().isEmpty()) {
                sb.append("| Comment | ").append(m.tableComment()).append(" |\n");
            }
            sb.append("\n");
        }

        var tableTriggers =
                mysqlInfo.triggers().stream()
                        .filter(
                                t ->
                                        t.schema().equals(schemaName)
                                                && t.tableName().equals(tableName))
                        .toList();
        if (!tableTriggers.isEmpty()) {
            sb.append("## Triggers\n\n");
            sb.append("| Name | Timing | Event | Statement | Definer |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (var trig : tableTriggers) {
                sb.append("| ")
                        .append(trig.name())
                        .append(" | ")
                        .append(trig.timing())
                        .append(" | ")
                        .append(trig.event())
                        .append(" | ")
                        .append(trig.statement())
                        .append(" | ")
                        .append(trig.definer() != null ? trig.definer() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var tablePartitions =
                mysqlInfo.partitions().stream()
                        .filter(
                                p ->
                                        p.schema().equals(schemaName)
                                                && p.tableName().equals(tableName))
                        .toList();
        if (!tablePartitions.isEmpty()) {
            sb.append("## Partition Info\n\n");
            sb.append("| Method | Expression | Count |\n");
            sb.append("| --- | --- | --- |\n");
            for (var part : tablePartitions) {
                sb.append("| ")
                        .append(part.partitionMethod())
                        .append(" | ")
                        .append(
                                part.partitionExpression() != null
                                        ? part.partitionExpression()
                                        : "")
                        .append(" | ")
                        .append(part.partitionCount())
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    /**
     * Appends per-schema MySQL sections to the documentation index and writes detail files.
     *
     * <p>For the named schema this emits index sections for routines, triggers, events, and
     * partitions (each only when present). Routines additionally get their own detail Markdown file
     * written under {@code <outputDir>/<name>/<schema>/routines/<routine>.md}.
     *
     * @param sb the buffer accumulating the index page Markdown
     * @param schemaName the name of the schema being rendered
     * @param outputDir the root output directory under which routine detail files are written
     */
    @Override
    protected void appendSchemaIndexSections(StringBuilder sb, String schemaName, Path outputDir) {
        var routines =
                mysqlInfo.routines().stream().filter(r -> r.schema().equals(schemaName)).toList();
        if (!routines.isEmpty()) {
            sb.append("### Routines\n\n");
            for (var routine : routines) {
                sb.append("- [")
                        .append(routine.name())
                        .append("](")
                        .append(name())
                        .append("/")
                        .append(schemaName)
                        .append("/routines/")
                        .append(routine.name())
                        .append(".md)\n");
                var fileSb = new StringBuilder();
                fileSb.append("# ").append(routine.name()).append("\n\n");
                fileSb.append("| Property | Value |\n");
                fileSb.append("| --- | --- |\n");
                fileSb.append("| Schema | ").append(routine.schema()).append(" |\n");
                fileSb.append("| Type | ").append(routine.type()).append(" |\n");
                fileSb.append("| Data Type | ").append(routine.dataType()).append(" |\n");
                fileSb.append("| Parameters | ").append(routine.parameterList()).append(" |\n");
                fileSb.append("| Security | ").append(routine.securityType()).append(" |\n");
                if (routine.definer() != null && !routine.definer().isBlank()) {
                    fileSb.append("| Definer | ").append(routine.definer()).append(" |\n");
                }
                Path routineFile =
                        outputDir
                                .resolve(name())
                                .resolve(schemaName)
                                .resolve("routines")
                                .resolve(routine.name() + ".md");
                writeFile(routineFile, fileSb.toString());
            }
            sb.append("\n");
        }

        var triggers =
                mysqlInfo.triggers().stream().filter(t -> t.schema().equals(schemaName)).toList();
        if (!triggers.isEmpty()) {
            sb.append("### Triggers\n\n");
            sb.append("| Name | Table | Timing | Event | Statement | Definer |\n");
            sb.append("| --- | --- | --- | --- | --- | --- |\n");
            for (var trig : triggers) {
                sb.append("| ")
                        .append(trig.name())
                        .append(" | ")
                        .append(trig.tableName())
                        .append(" | ")
                        .append(trig.timing())
                        .append(" | ")
                        .append(trig.event())
                        .append(" | ")
                        .append(trig.statement())
                        .append(" | ")
                        .append(trig.definer() != null ? trig.definer() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var events =
                mysqlInfo.events().stream().filter(e -> e.schema().equals(schemaName)).toList();
        if (!events.isEmpty()) {
            sb.append("### Events\n\n");
            sb.append("| Name | Type | Interval | Status | Definer |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (var event : events) {
                String interval =
                        event.intervalValue() != null && event.intervalField() != null
                                ? event.intervalValue() + " " + event.intervalField()
                                : "";
                sb.append("| ")
                        .append(event.name())
                        .append(" | ")
                        .append(event.type())
                        .append(" | ")
                        .append(interval)
                        .append(" | ")
                        .append(event.status())
                        .append(" | ")
                        .append(event.definer() != null ? event.definer() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var partitions =
                mysqlInfo.partitions().stream().filter(p -> p.schema().equals(schemaName)).toList();
        if (!partitions.isEmpty()) {
            sb.append("### Partitions\n\n");
            sb.append("| Table | Method | Expression | Count |\n");
            sb.append("| --- | --- | --- | --- |\n");
            for (var part : partitions) {
                sb.append("| ")
                        .append(part.tableName())
                        .append(" | ")
                        .append(part.partitionMethod())
                        .append(" | ")
                        .append(
                                part.partitionExpression() != null
                                        ? part.partitionExpression()
                                        : "")
                        .append(" | ")
                        .append(part.partitionCount())
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }
}
