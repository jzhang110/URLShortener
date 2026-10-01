package com.schwab.urlshortener.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The demo UI is served at {@code /} with scoped security headers, without disturbing Swagger (ADR 0010). */
class UiIntegrationTest extends IntegrationTest {

    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self'; font-src 'self'; object-src 'none'; base-uri 'none'; "
            + "form-action 'self'; frame-ancestors 'none'";

    @Test
    void rootServesTheUiPage() {
        ApiResponse response = api.get("/");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Content-Type")).startsWith("text/html");
        assertThat(response.body()).contains("URL Shortener").contains("/assets/app.js");
    }

    @Test
    void rootCarriesTheUiSecurityHeaders() {
        ApiResponse response = api.get("/");

        assertThat(response.header("Content-Security-Policy")).isEqualTo(CSP);
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
    }

    // Two path segments, so the redirect route /{shortCode} cannot capture them.
    @ParameterizedTest(name = "{0} is served as {1} with nosniff")
    @CsvSource({"/assets/app.js, javascript", "/assets/styles.css, text/css"})
    void staticAssetsAreServedWithNosniff(String path, String contentType) {
        ApiResponse response = api.get(path);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Content-Type")).contains(contentType);
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.header("Content-Security-Policy")).isNull();
    }

    @Test
    void swaggerUiStillLoadsWithoutTheUiContentSecurityPolicy() {
        ApiResponse swaggerUi = api.get("/swagger-ui/index.html");
        ApiResponse apiDocs = api.get("/v3/api-docs");

        assertThat(swaggerUi.status()).isEqualTo(200);
        assertThat(swaggerUi.header("Content-Security-Policy")).isNull();
        assertThat(apiDocs.status()).isEqualTo(200);
        assertThat(apiDocs.json().get("paths").has("/")).as("the UI page is not an API operation").isFalse();
    }
}
