package com.schwab.urlshortener.url.generation;

/**
 * Produces system-generated candidate short codes.
 * Alternate system-generated strategies may implement this interface.
 * Implementations must be deterministic and stateless;
 * uniqueness is enforced by the database.
 */
public interface ShortCodeGenerator {

    /**
     * @param normalizedUrl the URL identity (output of {@code UrlNormalizer})
     * @param attempt       0 for the first candidate, incremented after each collision
     * @return a code matching {@code ^[0-9a-f]{6}$}
     */
    String generate(String normalizedUrl, int attempt);
}
