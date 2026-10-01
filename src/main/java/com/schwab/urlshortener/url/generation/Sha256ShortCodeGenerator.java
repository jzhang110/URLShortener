package com.schwab.urlshortener.url.generation;

import org.springframework.stereotype.Component;

/**
 * First six hex characters of SHA-256 over the normalized URL (attempt 0), or over
 * {@code normalizedUrl + " #" + attempt} for retries. The space cannot occur in a parsed URI,
 * so a retry input can never equal another URL's first-attempt input (see ADR 0003).
 */
@Component
class Sha256ShortCodeGenerator implements ShortCodeGenerator {

    static final int CODE_LENGTH = 6;

    @Override
    public String generate(String normalizedUrl, int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }
        String input = attempt == 0 ? normalizedUrl : normalizedUrl + " #" + attempt;
        return Sha256.hex(input).substring(0, CODE_LENGTH);
    }
}
