package io.github.kakusuke.migraphe.cli.resolver;

/** Thrown when a resolved JAR's SHA-256 does not match its locked pin. */
public class ChecksumMismatchException extends RuntimeException {

    public ChecksumMismatchException(String message) {
        super(message);
    }
}
