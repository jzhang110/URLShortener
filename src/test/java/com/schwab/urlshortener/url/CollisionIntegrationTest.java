package com.schwab.urlshortener.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import com.schwab.urlshortener.url.generation.ShortCodeGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Forces every URL onto the same code sequence ("000000", "000001", ...) so that collision
 * handling is exercised against the real database constraints.
 */
class CollisionIntegrationTest extends IntegrationTest {

    @MockitoBean
    private ShortCodeGenerator generator;

    @BeforeEach
    void everyUrlCollidesOnTheSameSequence() {
        when(generator.generate(anyString(), anyInt()))
                .thenAnswer(invocation -> String.format("%06d", invocation.getArgument(1, Integer.class)));
    }

    @Test
    void collidingUrlGetsNextCandidateWithoutOverwritingExistingMapping() {
        ApiResponse first = api.shorten("https://first.example/");
        ApiResponse second = api.shorten("https://second.example/");

        assertThat(first.shortCode()).isEqualTo("000000");
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.shortCode()).isEqualTo("000001");
        assertThat(api.get("/000000").header("Location")).isEqualTo("https://first.example/");
        assertThat(api.get("/000001").header("Location")).isEqualTo("https://second.example/");
    }

    @Test
    void exhaustedAttemptsReturn503WithoutCorruptingExistingMappings() {
        for (int i = 0; i < 10; i++) {
            assertThat(api.shorten("https://site" + i + ".example/").status()).isEqualTo(201);
        }

        ApiResponse eleventh = api.shorten("https://one-too-many.example/");

        assertThat(eleventh.status()).isEqualTo(503);
        assertThat(eleventh.json().get("code").asString()).isEqualTo("SHORT_CODE_UNAVAILABLE");
        assertThat(countRows("url_mapping")).isEqualTo(10);
        assertThat(api.get("/000000").header("Location")).isEqualTo("https://site0.example/");
    }

    @Test
    void concurrentCollidingUrlsGetDistinctCodesAndKeepTheirOwnDestinations() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String url = "https://racer" + i + ".example/";
                futures.add(pool.submit(() -> {
                    start.await();
                    return api.shorten(url);
                }));
            }
            start.countDown();

            List<String> codes = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ApiResponse response = futures.get(i).get();
                assertThat(response.status()).isEqualTo(201);
                codes.add(response.shortCode());
                assertThat(api.get("/" + response.shortCode()).header("Location"))
                        .isEqualTo("https://racer" + i + ".example/");
            }
            assertThat(codes).doesNotHaveDuplicates();
            assertThat(countRows("url_mapping")).isEqualTo(threads);
        } finally {
            pool.shutdownNow();
        }
    }
}
