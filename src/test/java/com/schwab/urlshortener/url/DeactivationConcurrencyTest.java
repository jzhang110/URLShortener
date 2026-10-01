package com.schwab.urlshortener.url;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Racing deactivations of one mapping are serialized by its row lock and converge on one state (ADR 0009). */
class DeactivationConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 16;

    @Test
    void concurrentDeactivationsConvergeOnOneDeactivationTime() throws Exception {
        String shortCode = api.shorten("https://example.com/contested").shortCode();

        List<ApiResponse> responses = deactivateConcurrently(shortCode);

        assertThat(responses).extracting(ApiResponse::status).containsOnly(200);
        assertThat(responses).extracting(r -> r.json().get("status").asString()).containsOnly("DEACTIVATED");
        String deactivatedAt = responses.getFirst().json().get("deactivatedAt").asString();
        assertThat(responses).extracting(r -> r.json().get("deactivatedAt").asString()).containsOnly(deactivatedAt);

        assertThat(countRows("url_mapping")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM url_mapping", String.class)).isEqualTo("DEACTIVATED");
        Instant stored = jdbc.queryForObject("SELECT deactivated_at FROM url_mapping", OffsetDateTime.class).toInstant();
        assertThat(stored).isEqualTo(Instant.parse(deactivatedAt));
        assertThat(api.get("/" + shortCode).status()).isEqualTo(410);
    }

    private List<ApiResponse> deactivateConcurrently(String shortCode) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return api.deactivate(shortCode);
                }));
            }
            List<ApiResponse> responses = new ArrayList<>();
            for (Future<ApiResponse> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
