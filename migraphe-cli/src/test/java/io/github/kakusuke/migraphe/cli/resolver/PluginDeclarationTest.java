package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginDeclarationTest {

    @Test
    void parsesCoordinateOnlyFormFromString() {
        PluginDeclaration result = PluginDeclaration.fromString("com.example:my-plugin:1.0.0");

        assertThat(result.coordinate().groupId()).isEqualTo("com.example");
        assertThat(result.coordinate().artifactId()).isEqualTo("my-plugin");
        assertThat(result.coordinate().version()).isEqualTo("1.0.0");
        assertThat(result.repositoryRef()).isEmpty();
    }

    @Test
    void rejectsMalformedCoordinateString() {
        assertThatThrownBy(() -> PluginDeclaration.fromString("com.example:my-plugin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesCoordinateAndRepositoryFromMap() {
        PluginDeclaration result =
                PluginDeclaration.fromMap(
                        Map.of(
                                "coordinate",
                                "com.example:my-plugin:1.0.0",
                                "repository",
                                "jitpack"));

        assertThat(result.coordinate().groupId()).isEqualTo("com.example");
        assertThat(result.coordinate().artifactId()).isEqualTo("my-plugin");
        assertThat(result.coordinate().version()).isEqualTo("1.0.0");
        assertThat(result.repositoryRef()).contains("jitpack");
    }

    @Test
    void parsesCoordinateOnlyFromMap() {
        PluginDeclaration result =
                PluginDeclaration.fromMap(Map.of("coordinate", "com.example:my-plugin:1.0.0"));

        assertThat(result.coordinate().groupId()).isEqualTo("com.example");
        assertThat(result.repositoryRef()).isEmpty();
    }

    @Test
    void rejectsMapMissingCoordinateKey() {
        assertThatThrownBy(() -> PluginDeclaration.fromMap(Map.of("repository", "jitpack")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinate");
    }

    @Test
    void rejectsMapWithNonStringCoordinate() {
        assertThatThrownBy(() -> PluginDeclaration.fromMap(Map.of("coordinate", 42)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinate");
    }
}
