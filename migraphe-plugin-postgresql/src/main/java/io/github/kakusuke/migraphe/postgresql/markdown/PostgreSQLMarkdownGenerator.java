package io.github.kakusuke.migraphe.postgresql.markdown;

import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownGenerator;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;
import java.nio.file.Path;
import java.util.List;

public class PostgreSQLMarkdownGenerator extends JdbcMarkdownGenerator {

    private final PostgreSQLSchemaInfo pgInfo;

    public PostgreSQLMarkdownGenerator(
            String name,
            PostgreSQLSchemaInfo schemaInfo,
            List<JdbcMarkdownDefinition.ExcludePattern> excludes) {
        super(name, schemaInfo, excludes);
        this.pgInfo = schemaInfo;
    }

    @Override
    protected List<String> extraTableIndexHeaders() {
        return pgInfo.tableOwners().isEmpty() ? List.of() : List.of("Owner");
    }

    @Override
    protected List<String> extraTableIndexCells(String schemaName, JdbcTableInfo table) {
        if (pgInfo.tableOwners().isEmpty()) {
            return List.of();
        }
        String owner = pgInfo.tableOwners().getOrDefault(schemaName + "." + table.name(), "");
        return List.of(owner);
    }

    @Override
    protected List<String> extraViewIndexHeaders() {
        return pgInfo.viewOwners().isEmpty() ? List.of() : List.of("Owner");
    }

    @Override
    protected List<String> extraViewIndexCells(String schemaName, JdbcViewInfo view) {
        if (pgInfo.viewOwners().isEmpty()) {
            return List.of();
        }
        String owner = pgInfo.viewOwners().getOrDefault(schemaName + "." + view.name(), "");
        return List.of(owner);
    }

    @Override
    protected void appendTableFileHeader(StringBuilder sb, String schemaName, JdbcTableInfo table) {
        String owner = pgInfo.tableOwners().get(schemaName + "." + table.name());
        if (owner != null && !owner.isBlank()) {
            sb.append("Owner: ").append(owner).append("\n\n");
        }
    }

    @Override
    protected void appendViewFileHeader(StringBuilder sb, String schemaName, JdbcViewInfo view) {
        String owner = pgInfo.viewOwners().get(schemaName + "." + view.name());
        if (owner != null && !owner.isBlank()) {
            sb.append("Owner: ").append(owner).append("\n\n");
        }
    }

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
                String ownedBy =
                        (seq.ownerTable() != null && seq.ownerColumn() != null)
                                ? seq.ownerTable() + "." + seq.ownerColumn()
                                : "";
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
}
