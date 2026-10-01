package com.schwab.urlshortener.url.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256ShortCodeGeneratorTest {

    private final Sha256ShortCodeGenerator generator = new Sha256ShortCodeGenerator();

    // Vectors computed independently with `printf '%s' "<input>" | sha256sum`.
    @Test
    void firstAttemptHashesTheNormalizedUrlOnly() {
        assertThat(generator.generate("https://example.com/", 0)).isEqualTo("0f115d");
    }

    @Test
    void retryAttemptsHashTheUrlWithSpaceHashAttemptSuffix() {
        assertThat(generator.generate("https://example.com/", 1)).isEqualTo("cb7cd4");
        assertThat(generator.generate("https://example.com/", 2)).isEqualTo("99a098");
    }

    @Test
    void isDeterministic() {
        assertThat(generator.generate("https://example.com/x", 3))
                .isEqualTo(generator.generate("https://example.com/x", 3));
    }

    @Test
    void producesSixLowercaseHexCharacters() {
        assertThat(generator.generate("https://example.com/any?q=1", 0)).matches("^[0-9a-f]{6}$");
    }

    @Test
    void rejectsNegativeAttempt() {
        assertThatThrownBy(() -> generator.generate("https://example.com/", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
