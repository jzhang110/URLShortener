package com.schwab.urlshortener.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Immutable, startup-validated application settings. Safe to share across request threads.
 */
@Validated
@ConfigurationProperties("app.shortener")
public record ShortenerProperties(
        @NotNull URI baseUrl,
        @NotEmpty List<String> ownHosts,
        @Min(1) @Max(100) int maxAttempts,
        @Min(1) @Max(2048) int maxUrlLength) {

    /**
     * Hosts that identify this service; shortening a URL on one of them would create a redirect loop.
     * Returned as configured; the self-reference policy canonicalizes them for comparison.
     */
    public Set<String> selfHosts() {
        return Stream.concat(ownHosts.stream(), Stream.ofNullable(baseUrl.getHost()))
                .map(String::strip)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String shortUrlFor(String shortCode) {
        String base = baseUrl.toString();
        return (base.endsWith("/") ? base : base + "/") + shortCode;
    }
}
