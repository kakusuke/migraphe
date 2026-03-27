package io.github.kakusuke.migraphe.api.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MigraphePluginSchemaInfoProviderTest {

    @Test
    void schemaInfoProviderReturnsEmptyOptionalByDefault() {
        MigraphePlugin<?> plugin =
                new MigraphePlugin<String>() {
                    @Override
                    public String type() {
                        return "stub";
                    }

                    @Override
                    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
                        return StubTaskDefinition.class;
                    }

                    @Override
                    public Class<? extends EnvironmentDefinition> environmentDefinitionClass() {
                        return StubEnvironmentDefinition.class;
                    }

                    @Override
                    public EnvironmentProvider environmentProvider() {
                        return (name, definition) -> {
                            throw new UnsupportedOperationException();
                        };
                    }

                    @Override
                    public MigrationNodeProvider<String> migrationNodeProvider() {
                        return (nodeId, task, dependencies, environment) -> {
                            throw new UnsupportedOperationException();
                        };
                    }

                    @Override
                    public HistoryRepositoryProvider historyRepositoryProvider() {
                        return environment -> {
                            throw new UnsupportedOperationException();
                        };
                    }
                };

        assertThat(plugin.schemaInfoProvider()).isEmpty();
    }

    interface StubTaskDefinition extends TaskDefinition<String> {
        @Override
        default String name() {
            return "stub";
        }

        @Override
        default Optional<String> description() {
            return Optional.empty();
        }

        @Override
        default String target() {
            return "stub";
        }

        @Override
        default Optional<List<String>> dependencies() {
            return Optional.empty();
        }

        @Override
        default String up() {
            return "";
        }

        @Override
        default Optional<String> down() {
            return Optional.empty();
        }
    }

    interface StubEnvironmentDefinition extends EnvironmentDefinition {}
}
