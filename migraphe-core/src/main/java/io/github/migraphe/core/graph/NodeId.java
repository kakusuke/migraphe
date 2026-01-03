package io.github.migraphe.core.graph;

import java.util.Objects;
import java.util.UUID;

/** ノードの一意識別子。 */
public record NodeId(String value) {

    public NodeId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeId value must not be blank");
        }
    }

    /** UUIDベースのNodeIdを生成 */
    public static NodeId generate() {
        return new NodeId(UUID.randomUUID().toString());
    }

    /** 指定された文字列からNodeIdを生成 */
    public static NodeId of(String value) {
        return new NodeId(value);
    }
}
