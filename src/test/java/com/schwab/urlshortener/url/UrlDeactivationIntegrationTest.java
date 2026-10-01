package com.schwab.urlshortener.url;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** URL deactivation through the HTTP API and the real database (ADR 0009). */
class UrlDeactivationIntegrationTest extends IntegrationTest {

    private static final String DESTINATION = "https://example.com/a";

    private String shortCode;

    @BeforeEach
    void createMapping() {
        shortCode = api.shorten(DESTINATION).shortCode();
    }

    @Test
    void activeShortUrlRedirects() {
        assertThat(api.get("/" + shortCode).status()).isEqualTo(302);
    }

    @Test
    void deactivatingAnActiveUrlReturnsTheDeactivatedState() {
        ApiResponse response = api.deactivate(shortCode);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("shortCode").asString()).isEqualTo(shortCode);
        assertThat(response.json().get("status").asString()).isEqualTo("DEACTIVATED");
        assertThat(Instant.parse(response.json().get("deactivatedAt").asString())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM url_mapping", String.class)).isEqualTo("DEACTIVATED");
    }

    @Test
    void repeatedDeactivationIsIdempotentAndKeepsTheOriginalTime() {
        ApiResponse first = api.deactivate(shortCode);
        ApiResponse second = api.deactivate(shortCode);

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json().get("status").asString()).isEqualTo("DEACTIVATED");
        assertThat(second.json().get("deactivatedAt").asString())
                .isEqualTo(first.json().get("deactivatedAt").asString());
    }

    @Test
    void deactivatingAnUnknownCodeIsNotFoundAndCreatesNothing() {
        ApiResponse response = api.deactivate(unknownCodeOtherThan(shortCode));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asString()).isEqualTo("SHORT_CODE_NOT_FOUND");
        assertThat(countRows("url_mapping")).isEqualTo(1);
    }

    @Test
    void deactivatedUrlReturns410WithoutRedirectingOrRecordingAClick() {
        api.deactivate(shortCode);

        ApiResponse response = api.get("/" + shortCode);

        assertThat(response.status()).isEqualTo(410);
        assertThat(response.json().get("code").asString()).isEqualTo("SHORT_CODE_DEACTIVATED");
        assertThat(response.json().get("detail").asString()).isEqualTo("This short URL has been deactivated.");
        assertThat(response.header("Location")).isNull();
        assertThat(countRows("click_event")).isZero();
    }

    @Test
    void historicalAnalyticsRemainAvailableAfterDeactivation() {
        for (int i = 0; i < 3; i++) {
            api.get("/" + shortCode);
        }
        api.deactivate(shortCode);

        ApiResponse beforeAttempt = api.analytics(shortCode);
        api.get("/" + shortCode); // refused with 410; must not be counted
        ApiResponse afterAttempt = api.analytics(shortCode);

        assertThat(beforeAttempt.status()).isEqualTo(200);
        assertThat(beforeAttempt.json().get("totalClicks").asLong()).isEqualTo(3);
        assertThat(afterAttempt.status()).isEqualTo(200);
        assertThat(afterAttempt.json().get("totalClicks").asLong()).isEqualTo(3);
        assertThat(afterAttempt.json().get("lastClickedAt").asString()).isNotBlank();
    }

    @ParameterizedTest(name = "resubmitting {0} conflicts")
    @ValueSource(strings = {DESTINATION, "HTTPS://Example.com/a"}) // identical and equivalent (N2, N3) forms
    void reshorteningADeactivatedUrlConflictsWithoutCreatingOrReactivating(String resubmitted) {
        api.deactivate(shortCode);

        ApiResponse response = api.shorten(resubmitted);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("code").asString()).isEqualTo("URL_DEACTIVATED");
        assertThat(countRows("url_mapping")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM url_mapping", String.class)).isEqualTo("DEACTIVATED");
        assertThat(api.get("/" + shortCode).status()).isEqualTo(410);
    }
}
