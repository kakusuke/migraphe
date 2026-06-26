package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes SHA-256 hex digests of files via streaming reads (8 KiB chunks).
 *
 * <p>Used throughout the lockfile machinery to hash plugin and dependency JARs both when pinning
 * (see {@link LockFileBuilder}) and when verifying resolved artifacts against their pins. The file
 * is read incrementally so arbitrarily large JARs are hashed with constant memory. This is a
 * stateless utility class and cannot be instantiated.
 */
public final class Sha256Calculator {

    private static final int CHUNK_SIZE = 8192;

    private Sha256Calculator() {}

    /**
     * Computes the SHA-256 digest of the given file as a lowercase hex string.
     *
     * @param file the file to hash
     * @return the SHA-256 digest as 64 lowercase hexadecimal characters
     * @throws IOException if the file cannot be opened or read
     * @throws IllegalStateException if the JVM does not provide the SHA-256 algorithm
     */
    public static String hash(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
