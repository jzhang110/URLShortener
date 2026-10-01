package com.schwab.urlshortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** One test per normalization rule (ADR 0002), plus the things deliberately NOT normalized. */
class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();
    private final UrlValidator validator = new UrlValidator(
            new ShortenerProperties(URI.create("https://sho.rt"), List.of("localhost"), 10, 2048));

    /** Same path as production: the validator parses the trimmed input (N1), the normalizer applies N2-N6. */
    private String normalize(String raw) {
        return normalizer.normalize(validator.validate(raw));
    }

    @Test
    void n1_trimsSurroundingWhitespace() {
        assertThat(normalize("  https://example.com/a \t")).isEqualTo("https://example.com/a");
    }

    @Test
    void n2_lowercasesScheme() {
        assertThat(normalize("HTTPS://example.com/a")).isEqualTo("https://example.com/a");
    }

    @Test
    void n3_lowercasesHost() {
        assertThat(normalize("https://Example.COM/a")).isEqualTo("https://example.com/a");
    }

    @Test
    void n4_removesDefaultHttpsPort() {
        assertThat(normalize("https://example.com:443/a")).isEqualTo("https://example.com/a");
    }

    @Test
    void n4_removesDefaultHttpPort() {
        assertThat(normalize("http://example.com:80/a")).isEqualTo("http://example.com/a");
    }

    @Test
    void n5_emptyPathBecomesRoot() {
        assertThat(normalize("https://example.com")).isEqualTo("https://example.com/");
    }

    @Test
    void n5_emptyPathBecomesRootEvenWithQuery() {
        assertThat(normalize("https://example.com?q=1")).isEqualTo("https://example.com/?q=1");
    }

    @Test
    void n6_keepsPathQueryAndFragmentVerbatim() {
        assertThat(normalize("https://example.com/A%2Fb/./c?Z=1&a=%20#Frag"))
                .isEqualTo("https://example.com/A%2Fb/./c?Z=1&a=%20#Frag");
    }

    @Test
    void combinedRulesMakeEquivalentFormsIdentical() {
        assertThat(normalize("HTTPS://Example.COM:443"))
                .isEqualTo(normalize("https://example.com/"));
    }

    @Nested
    class NotNormalized {

        @Test
        void nonDefaultPortIsKept() {
            assertThat(normalize("https://example.com:8443/")).isEqualTo("https://example.com:8443/");
        }

        @Test
        void httpPortOnHttpsIsKept() {
            assertThat(normalize("https://example.com:80/")).isEqualTo("https://example.com:80/");
        }

        @Test
        void trailingSlashOnNonRootPathIsKept() {
            assertThat(normalize("https://example.com/path/")).isEqualTo("https://example.com/path/");
            assertThat(normalize("https://example.com/path")).isEqualTo("https://example.com/path");
        }

        @Test
        void pathCaseIsKept() {
            assertThat(normalize("https://example.com/A")).isEqualTo("https://example.com/A");
        }

        @Test
        void fragmentIsKept() {
            assertThat(normalize("https://example.com/page#a")).isEqualTo("https://example.com/page#a");
            assertThat(normalize("https://example.com/page#a"))
                    .isNotEqualTo(normalize("https://example.com/page#b"))
                    .isNotEqualTo(normalize("https://example.com/page"));
        }

        @Test
        void queryParameterOrderIsKept() {
            assertThat(normalize("https://example.com/?b=2&a=1")).isEqualTo("https://example.com/?b=2&a=1");
        }
    }
}
