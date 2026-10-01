package com.schwab.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsIntegrationTest extends IntegrationTest {

    private String shortCode;

    @BeforeEach
    void createMapping() {
        shortCode = api.shorten("https://example.com/popular").shortCode();
    }

    @Test
    void newShortUrlHasNoClicks() {
        ApiResponse response = api.analytics(shortCode);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("shortCode").asString()).isEqualTo(shortCode);
        assertThat(response.json().get("totalClicks").asLong()).isZero();
        assertThat(response.json().get("lastClickedAt").isNull()).isTrue();
    }

    @Test
    void countsEachSuccessfulRedirect() {
        for (int i = 0; i < 3; i++) {
            api.get("/" + shortCode);
        }

        ApiResponse response = api.analytics(shortCode);

        assertThat(response.json().get("totalClicks").asLong()).isEqualTo(3);
        assertThat(response.json().get("lastClickedAt").asString()).isNotBlank();
    }

    @Test
    void failedRedirectsAreNotCounted() {
        api.get("/" + shortCode);
        api.get("/" + unknownCodeOtherThan(shortCode));
        api.get("/not-a-code");

        assertThat(api.totalClicks(shortCode)).isEqualTo(1);
    }

    @Test
    void clicksAreCountedPerMapping() {
        String other = api.shorten("https://example.com/other").shortCode();
        api.get("/" + shortCode);
        api.get("/" + shortCode);
        api.get("/" + other);

        assertThat(api.totalClicks(shortCode)).isEqualTo(2);
        assertThat(api.totalClicks(other)).isEqualTo(1);
    }

    @Test
    void unknownShortCodeReturns404() {
        ApiResponse response = api.analytics(unknownCodeOtherThan(shortCode));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asString()).isEqualTo("SHORT_CODE_NOT_FOUND");
    }

    @Test
    void concurrentRedirectsAreAllCounted() throws Exception {
        int clicks = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < clicks; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return api.get("/" + shortCode).status();
                }));
            }
            start.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get()).isEqualTo(302);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(api.totalClicks(shortCode)).isEqualTo(clicks);
    }
}
