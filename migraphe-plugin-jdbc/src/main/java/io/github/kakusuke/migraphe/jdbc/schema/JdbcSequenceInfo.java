package io.github.kakusuke.migraphe.jdbc.schema;

/** シーケンス情報。 */
public record JdbcSequenceInfo(
        String name,
        String dataType,
        long startValue,
        long increment,
        long minValue,
        long maxValue,
        boolean cycle) {}
