package io.github.kakusuke.migraphe.mysql.markdown;

import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownGenerator;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import java.nio.file.Path;
import java.util.List;

public class MySQLMarkdownGenerator extends JdbcMarkdownGenerator {

    private final MySQLSchemaInfo mysqlInfo;

    public MySQLMarkdownGenerator(
            String name,
            MySQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes) {
        super(name, schemaInfo, excludes);
        this.mysqlInfo = schemaInfo;
    }

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
            sb.append("| Name | Timing | Event | Statement |\n");
            sb.append("| --- | --- | --- | --- |\n");
            for (var trig : tableTriggers) {
                sb.append("| ")
                        .append(trig.name())
                        .append(" | ")
                        .append(trig.timing())
                        .append(" | ")
                        .append(trig.event())
                        .append(" | ")
                        .append(trig.statement())
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
            sb.append("| Name | Table | Timing | Event | Statement |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
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
                        .append(" |\n");
            }
            sb.append("\n");
        }

        var events =
                mysqlInfo.events().stream().filter(e -> e.schema().equals(schemaName)).toList();
        if (!events.isEmpty()) {
            sb.append("### Events\n\n");
            sb.append("| Name | Type | Interval | Status |\n");
            sb.append("| --- | --- | --- | --- |\n");
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
