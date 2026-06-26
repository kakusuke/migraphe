package io.github.kakusuke.migraphe.cli.command;

/**
 * A single executable CLI command.
 *
 * <p>Each Migraphe subcommand ({@code up}, {@code down}, {@code status}, {@code validate}, {@code
 * generate}, {@code pin}) implements this interface. {@link io.github.kakusuke.migraphe.cli.Main}
 * parses the command-line arguments, constructs the appropriate implementation, and invokes {@link
 * #execute()} to run it. The returned value becomes the process exit code.
 */
public interface Command {

    /**
     * Executes the command.
     *
     * @return the process exit code: {@code 0} on success, a non-zero value on error
     */
    int execute();
}
