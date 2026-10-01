package com.schwab.urlshortener.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin HTTP client for black-box tests against the running application. Never follows redirects,
 * so tests observe the 302 itself. Thread-safe (HttpClient and JsonMapper are).
 */
public final class TestApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String baseUrl;

    public TestApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public ApiResponse shorten(String url) {
        return postJson("/api/v1/urls", JSON.writeValueAsString(Map.of("url", url)));
    }

    public ApiResponse postJson(String path, String body) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    public ApiResponse get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    public ApiResponse get(String path, String headerName, String headerValue) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).header(headerName, headerValue).GET());
    }

    public ApiResponse deactivate(String shortCode) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/urls/" + shortCode + "/deactivate"))
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    public ApiResponse analytics(String shortCode) {
        return get("/api/v1/urls/" + shortCode + "/analytics");
    }

    public long totalClicks(String shortCode) {
        return analytics(shortCode).json().get("totalClicks").asLong();
    }

    private ApiResponse send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public record ApiResponse(int status, Map<String, List<String>> headers, String body) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }

        public String shortCode() {
            return json().get("shortCode").asString();
        }
    }
}
