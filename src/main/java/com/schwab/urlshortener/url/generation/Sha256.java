package com.schwab.urlshortener.url.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 as lowercase hex. A new MessageDigest per call because MessageDigest is not thread-safe. */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every compliant JVM ships SHA-256, so this is a broken runtime, not a recoverable condition.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
