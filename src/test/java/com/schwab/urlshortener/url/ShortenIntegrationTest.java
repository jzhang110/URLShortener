package com.schwab.urlshortener.url;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShortenIntegrationTest extends IntegrationTest {

    @Test
    void createsShortUrlWith201AndLocation() {
        ApiResponse response = api.shorten("https://example.com/articles/1?ref=test");

        assertThat(response.status()).isEqualTo(201);
        String shortCode = response.shortCode();
        assertThat(shortCode).matches("^[0-9a-f]{6}$");
        assertThat(response.json().get("shortUrl").asString()).isEqualTo("http://localhost:8080/" + shortCode);
        assertThat(response.header("Location")).isEqualTo("http://localhost:8080/" + shortCode);
        assertThat(response.json().get("destinationUrl").asString()).isEqualTo("https://example.com/articles/1?ref=test");
        assertThat(response.json().get("createdAt").asString()).isNotBlank();
        assertThat(countRows("url_mapping")).isEqualTo(1);
    }

    @Test
    void acceptsMaximumLengthUrlWhoseNormalizationAddsRootPath() {
        String prefix = "https://example.com?q=";
        String url = prefix + "a".repeat(2048 - prefix.length());
        assertThat(url).hasSize(2048);

        ApiResponse response = api.shorten(url);

        assertThat(response.status()).isEqualTo(201);
        String normalized = jdbc.queryForObject("SELECT normalized_url FROM url_mapping", String.class);
        assertThat(normalized).hasSize(2049).startsWith("https://example.com/?q=");
    }

    @Test
    void identicalUrlReturnsExistingMappingWith200() {
        ApiResponse first = api.shorten("https://example.com/same");
        ApiResponse second = api.shorten("https://example.com/same");

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(countRows("url_mapping")).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} is equivalent to https://example.com/")
    @ValueSource(strings = {
            "HTTPS://example.com/",     // N2
            "https://Example.COM/",     // N3
            "https://example.com:443/", // N4
            "https://example.com",      // N5
            "  https://example.com/  "  // N1
    })
    void equivalentUrlsUnderNormalizationRulesShareOneMapping(String equivalent) {
        ApiResponse original = api.shorten("https://example.com/");
        ApiResponse duplicate = api.shorten(equivalent);

        assertThat(duplicate.status()).isEqualTo(200);
        assertThat(duplicate.shortCode()).isEqualTo(original.shortCode());
        assertThat(duplicate.json().get("destinationUrl").asString())
                .as("first-submitted destination is kept")
                .isEqualTo("https://example.com/");
        assertThat(countRows("url_mapping")).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} and {1} are distinct")
    @CsvSource({
            "https://example.com/,     https://example.com:8443/",
            "https://example.com/path, https://example.com/path/",
            "https://example.com/a,    https://example.com/A",
            "http://example.com/,      https://example.com/"
    })
    void urlsOutsideTheNormalizationRulesAreDistinct(String first, String second) {
        ApiResponse a = api.shorten(first);
        ApiResponse b = api.shorten(second);

        assertThat(b.status()).isEqualTo(201);
        assertThat(b.shortCode()).isNotEqualTo(a.shortCode());
    }
}
