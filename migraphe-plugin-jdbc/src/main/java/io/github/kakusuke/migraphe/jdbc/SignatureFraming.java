package io.github.kakusuke.migraphe.jdbc;

/**
 * Frames the parts a JDBC task's {@code signature()} is built from.
 *
 * <p>A signature composed of several fields has to keep their boundaries, for the same reason core
 * frames the signatures it folds: concatenating SQL with a mode marker lets a statement ending in
 * the marker's own text produce the token of a statement that does not, with the mode set.
 *
 * <p>Each part is written as {@code <length>:<text>}, so a reader consumes exactly as many
 * characters as the length announces and no text can be mistaken for a boundary.
 */
final class SignatureFraming {

    private SignatureFraming() {}

    /**
     * Frames one statement and the mode it runs in.
     *
     * @param sql the statement text, already stripped
     * @param autocommit whether the statement runs outside an enclosing transaction
     * @return the framed pre-image for a task signature
     */
    static String frame(String sql, boolean autocommit) {
        return part(sql) + part(autocommit ? "t" : "f");
    }

    private static String part(String text) {
        return text.length() + ":" + text;
    }
}
