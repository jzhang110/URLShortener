package com.schwab.urlshortener.url.api.dto;

import com.schwab.urlshortener.url.domain.UrlMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A shortened URL")
public record UrlResponse(
        @Schema(description = "Six lowercase hexadecimal characters", example = "3f2a9c")
        String shortCode,
        @Schema(description = "Absolute short URL", example = "http://localhost:8080/3f2a9c")
        String shortUrl,
        @Schema(description = "Redirect target (first submitted form for equivalent URLs)",
                example = "https://example.com/some/long/path?q=1")
        String destinationUrl,
        @Schema(description = "When the mapping was created (UTC)", example = "2026-09-30T12:00:00Z")
        Instant createdAt) {

    public static UrlResponse from(UrlMapping mapping, String shortUrl) {
        return new UrlResponse(mapping.getShortCode(), shortUrl, mapping.getDestinationUrl(),
                mapping.getCreatedAt());
    }
}
