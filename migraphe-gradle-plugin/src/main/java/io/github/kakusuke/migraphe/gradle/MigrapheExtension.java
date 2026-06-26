package io.github.kakusuke.migraphe.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;

/**
 * Gradle DSL extension for the Migraphe plugin.
 *
 * <p>Registered under the {@code migraphe} name by {@link MigrapheGradlePlugin}, this extension
 * exposes the build-script configuration that is shared by every Migraphe task ({@code migrapheUp},
 * {@code migrapheDown}, {@code migrapheStatus}, {@code migrapheValidate} and {@code
 * migrapheGenerate}). The plugin wires these properties onto each task as conventions, so a value
 * set once in the {@code migraphe { ... }} block applies to all tasks.
 *
 * <p>Because Gradle implements abstract property getters lazily, this type is declared {@code
 * abstract} and instantiated by Gradle's {@code ObjectFactory}; build scripts never construct it
 * directly.
 */
public abstract class MigrapheExtension {

    /** Constructor for use by Gradle's managed-type generation. */
    public MigrapheExtension() {}

    /**
     * Returns the base directory under which the Migraphe configuration files (such as {@code
     * migraphe.yaml}, {@code targets/}, {@code tasks/} and {@code environments/}) are located.
     *
     * <p>Defaults to the project directory.
     *
     * @return the configurable base-directory property
     */
    public abstract DirectoryProperty getBaseDir();

    /**
     * Returns the user-supplied variables that are injected into the SmallRye configuration and
     * used to resolve {@code ${VAR}} references in the YAML configuration.
     *
     * <p>Defaults to an empty map.
     *
     * @return the configurable variable map property
     */
    public abstract MapProperty<String, String> getVariables();
}
