package com.schwab.urlshortener.url;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Duplicate submissions racing each other must converge on one mapping (DB uniqueness is the authority). */
class ShortenConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 32;

    @Test
    void concurrentIdenticalSubmissionsCreateExactlyOneMapping() throws Exception {
        List<ApiResponse> responses = submitConcurrently(i -> "https://example.com/hot-link");

        assertThat(responses).extracting(ApiResponse::shortCode).containsOnly(responses.getFirst().shortCode());
        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 200).hasSize(THREADS - 1);
        assertThat(countRows("url_mapping")).isEqualTo(1);
    }

    @Test
    void concurrentEquivalentSubmissionsCreateExactlyOneMapping() throws Exception {
        String[] forms = {"https://example.com/", "HTTPS://EXAMPLE.COM", "https://example.com:443/"};
        List<ApiResponse> responses = submitConcurrently(i -> forms[i % forms.length]);

        assertThat(responses).extracting(ApiResponse::shortCode).containsOnly(responses.getFirst().shortCode());
        assertThat(countRows("url_mapping")).isEqualTo(1);
    }

    private List<ApiResponse> submitConcurrently(IntFunction<String> urlForThread)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String url = urlForThread.apply(i);
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return api.shorten(url);
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
