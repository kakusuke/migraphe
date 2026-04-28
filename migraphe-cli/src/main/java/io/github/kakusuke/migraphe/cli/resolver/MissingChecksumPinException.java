package io.github.kakusuke.migraphe.cli.resolver;

/** Thrown when a resolved artifact has no corresponding pin in the lockfile. */
public class MissingChecksumPinException extends RuntimeException {

    public MissingChecksumPinException(String message) {
        super(message);
    }
}
