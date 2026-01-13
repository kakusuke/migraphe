package io.github.migraphe.api.environment;

/** マイグレーション実行環境のインターフェース（dev、staging、prodなど）。 プラグインがこのインターフェースを実装して、具体的な環境を定義する。 */
public interface Environment {

    /** 環境の一意識別子 */
    EnvironmentId id();

    /** 環境名（例: "dev", "staging", "prod"） */
    String name();
}
