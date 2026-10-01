package com.schwab.urlshortener.url.service;

import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Produces the URL identity used for duplicate detection and hashing.
 * The rules N1-N6 below are the complete normalization spec (ADR 0002); nothing else is normalized.
 */
@Component
public class UrlNormalizer {

    /**
     * @param uri the URI returned by {@link UrlValidator}, which already applied N1 (trim) before parsing
     */
    public String normalize(URI uri) {
        StringBuilder normalized = new StringBuilder()
                .append(uri.getScheme().toLowerCase(Locale.ROOT))   // N2 scheme case
                .append("://")
                .append(uri.getHost().toLowerCase(Locale.ROOT));    // N3 host case
        if (uri.getPort() != -1 && uri.getPort() != defaultPort(uri.getScheme())) {
            normalized.append(':').append(uri.getPort());           // N4 keep non-default port only
        }
        String rawPath = uri.getRawPath();
        normalized.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath); // N5 empty path -> "/"
        if (uri.getRawQuery() != null) {                            // N6 query and fragment verbatim
            normalized.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            normalized.append('#').append(uri.getRawFragment());
        }
        return normalized.toString();
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
