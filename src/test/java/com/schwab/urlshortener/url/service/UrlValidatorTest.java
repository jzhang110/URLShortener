package com.schwab.urlshortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.InvalidUrlException.Reason;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private static final int MAX_LENGTH = 2048;

    private final UrlValidator validator = new UrlValidator(
            new ShortenerProperties(URI.create("http://localhost:8080"), List.of("localhost"), 10, MAX_LENGTH));

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1#frag",
            "HTTPS://EXAMPLE.COM:8443/",
            "  https://example.com/trimmed  ",
            "https://[2001:db8::1]/ipv6"
    })
    void acceptsValidHttpAndHttpsUrls(String url) {
        URI uri = validator.validate(url);
        assertThat(uri.getHost()).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsMissingUrl(String url) {
        assertRejected(url, Reason.URL_REQUIRED);
    }

    @Test
    void rejectsUrlLongerThanLimit() {
        String url = "https://example.com/" + "a".repeat(MAX_LENGTH - "https://example.com/".length() + 1);
        assertRejected(url, Reason.URL_TOO_LONG);
    }

    @Test
    void acceptsUrlExactlyAtLimit() {
        String url = "https://example.com/" + "a".repeat(MAX_LENGTH - "https://example.com/".length());
        assertThat(validator.validate(url)).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"https://example.com:1/, 1", "https://example.com:65535/, 65535", "https://example.com/, -1"})
    void acceptsPortsInRangeOrAbsent(String url, int port) {
        assertThat(validator.validate(url).getPort()).isEqualTo(port);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://exa mple.com       | MALFORMED_URL",
            "https://example.com/<x>    | MALFORMED_URL",
            "http://[not-ipv6/          | MALFORMED_URL",
            "javascript:alert(1)        | UNSUPPORTED_SCHEME",
            "JavaScript:alert(1)        | UNSUPPORTED_SCHEME",
            "data:text/html,<b>x</b>    | MALFORMED_URL",
            "ftp://example.com/file     | UNSUPPORTED_SCHEME",
            "file:///etc/passwd         | UNSUPPORTED_SCHEME",
            "mailto:someone@example.com | UNSUPPORTED_SCHEME",
            "example.com/no-scheme      | UNSUPPORTED_SCHEME",
            "https:///path-only         | MISSING_HOST",
            "http:opaque                | MISSING_HOST",
            "https://user:pw@example.com/ | USERINFO_NOT_ALLOWED",
            "https://example.com:0      | INVALID_PORT",
            "https://example.com:65536  | INVALID_PORT",
            "https://example.com:99999  | INVALID_PORT"
    })
    void rejectsInvalidUrls(String url, Reason expected) {
        assertRejected(url, expected);
    }

    private void assertRejected(String url, Reason expected) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOfSatisfying(InvalidUrlException.class, e -> assertThat(e.reason()).isEqualTo(expected));
    }
}
