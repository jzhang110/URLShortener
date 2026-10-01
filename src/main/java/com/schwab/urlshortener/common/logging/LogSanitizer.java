package com.schwab.urlshortener.common.logging;

/**
 * Neutralizes untrusted values before they are written to logs, preventing forged log lines.
 */
public final class LogSanitizer {

    private static final int MAX_LENGTH = 100;

    private LogSanitizer() {
    }

    public static String sanitize(String untrusted) {
        if (untrusted == null) {
            return "null";
        }
        String singleLine = untrusted.replaceAll("[\\r\\n\\t]", "_");
        return singleLine.length() > MAX_LENGTH ? singleLine.substring(0, MAX_LENGTH) + "..." : singleLine;
    }
}
